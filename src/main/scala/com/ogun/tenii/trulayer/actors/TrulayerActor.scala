package com.ogun.tenii.trulayer.actors

import akka.actor.{Actor, ActorSystem}
import akka.stream.ActorMaterializer
import com.ogun.tenii.trulayer.external.HttpTransfers
import com.ogun.tenii.trulayer.model._
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Properties, Success}
import com.ogun.tenii.trulayer.helpers.JsonSupport

class TrulayerActor extends Actor with LazyLogging with TrulayerEndpoint with JsonSupport {

  implicit val system: ActorSystem = context.system
  implicit val mat: ActorMaterializer = ActorMaterializer()
  val clientId: String = Properties.envOrElse("CLIENT_ID", "something")
  val clientSecret: String = Properties.envOrElse("CLIENT_SECRET", "blabla")
  val redirectUrl = "https://tenii-demo.herokuapp.com/postauth"
  val http = new HttpTransfers()

  override def receive: Receive = {
    case req: Redirect =>
      val senderRef = sender()
      req.error match {
        case Some(err) =>
          logger.error(s"Error received $err")
          senderRef ! RedirectResponse(Nil, Some(s"Error from trulayer: $err"))
        case None =>
          if (validatePermissions(providedPermissions = providedPermissions(req.scope))) {
            implicit val timeout2: FiniteDuration = 10.seconds
            val url = s"$trulayerUrl$tokenEndpoint"
            val query = s"&grant_type=authorization_code&$clientIdParam$clientId&$clientSecretParam$clientSecret&$redirectParam$redirectUrl&$codeParam${req.code}"
            logger.info(s"url is $url$query")
            http.postAsForm[AccessTokenInfo](s"$url",Seq(("grant_type","authorization_code"),(clientIdParam,clientId),(clientSecretParam, clientSecret),(redirectParam, redirectUrl),(codeParam, req.code))) onComplete {
              case Success(token) =>
                http.endpointGetBearer[AccountResponse](s"$trulayerApi$accountsEndpoint", token.access_token) onComplete {
                  case Success(accounts) => senderRef ! RedirectResponse(accounts.results)
                  case Failure(t) =>
                    logger.error(s"Failed to get accounts", t)
                    senderRef ! RedirectResponse(Nil, Some(s"Failed to get accounts: $t"))
                }
              case Failure(t) =>
                logger.error(s"Failed to get access token", t)
                senderRef ! RedirectResponse(Nil, Some(s"Failed to get access token: $t"))
            }
          } else {
            senderRef ! RedirectResponse(Nil, Some("Required permissions not given"))
          }
      }

  }

  def validatePermissions(requestedPermissions: List[TrulayerPermissions] = requiredPermissions, providedPermissions: List[TrulayerPermissions], matches: Boolean = true): Boolean = {
    requestedPermissions match {
      case Nil => matches
      case head :: tail => if (!matches) {
        matches
      } else {
        validatePermissions(tail, providedPermissions, providedPermissions.contains(head))
      }
    }
  }

}

trait TrulayerEndpoint {
  val trulayerUrl = "https://auth.truelayer.com/"
  val trulayerApi = "https://api.truelayer.com/"
  val tokenEndpoint = "connect/token"
  val accountsEndpoint = "data/v1/accounts"
  val clientIdParam = "client_id"
  val clientSecretParam = "client_secret"
  val redirectParam = "redirect_uri"
  val codeParam = "code"
  def createPermissions(perm: String) = {
    perm.split("&").map(_.split("="))
      .map(perm => TrulayerPermissions(perm(0), calculateBool(perm(1)))).toList
  }

  def providedPermissions(perm: String) = {
    perm.split(" ").map(perm => TrulayerPermissions(perm, true)).toList
  }
  val requiredPermissions = List(TrulayerPermissions("info"), TrulayerPermissions("accounts"), TrulayerPermissions("balance"),
    TrulayerPermissions("transactions"), TrulayerPermissions("cards"), TrulayerPermissions("offline_access"))

  def calculateBool(bool: String): Boolean = {
    bool match {
      case "true" => true
      case _ => false
    }
  }

  implicit def onSuccessDecodingError[TellerResponse](decodingError: io.circe.Error): TellerResponse = throw new Exception(s"Error decoding trains upstream response: $decodingError")
  implicit def onErrorDecodingError[TellerResponse](decodingError: String): TellerResponse = throw new Exception(s"Error decoding upstream error response: $decodingError")
}