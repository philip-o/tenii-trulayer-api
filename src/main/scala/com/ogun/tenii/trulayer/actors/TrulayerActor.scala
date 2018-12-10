package com.ogun.tenii.trulayer.actors

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import com.ogun.tenii.trulayer.db.UserTokenConnection
import com.ogun.tenii.trulayer.external.HttpTransfers
import com.ogun.tenii.trulayer.model._
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Properties, Success}
import com.ogun.tenii.trulayer.helpers.{JsonSupport, UserUtil}
import com.ogun.tenii.trulayer.implicits.AccountImplicits
import com.ogun.tenii.trulayer.model.db.UserToken

import scala.collection.mutable
import scala.concurrent.Future

class TrulayerActor extends Actor with LazyLogging with TrulayerEndpoint with JsonSupport with AccountImplicits {

  implicit val system: ActorSystem = context.system
  implicit val mat: ActorMaterializer = ActorMaterializer()
  val clientId: String = Properties.envOrElse("CLIENT_ID", "something")
  val clientSecret: String = Properties.envOrElse("CLIENT_SECRET", "blabla")
  val redirectUrl = "https://tenii-demo.herokuapp.com/postauth"
  val http = new HttpTransfers()
  val refSize = mutable.Map[ActorRef, Int]()
  val refAccounts = mutable.Map[ActorRef, Seq[RedirectAccount]]()
  val connection = new UserTokenConnection

  override def receive: Receive = {
    case req: Redirect =>
      val senderRef = sender()
      req.error match {
        case Some(err) =>
          logger.error(s"Error received $err")
          senderRef ! RedirectResponse(Nil, error = Some(s"Error from trulayer: $err"))
        case None =>
          if (validatePermissions(providedPermissions = providedPermissions(req.scope))) {
            implicit val timeout2: FiniteDuration = 20.seconds
            val url = s"$trulayerUrl$tokenEndpoint"
            val query = s"&grant_type=authorization_code&$clientIdParam$clientId&$clientSecretParam$clientSecret&$redirectParam$redirectUrl&$codeParam${req.code}"
            logger.info(s"url is $url$query")
            http.postAsForm[AccessTokenInfo](s"$url",Seq(("grant_type","authorization_code"),(clientIdParam,clientId),(clientSecretParam, clientSecret),(redirectParam, redirectUrl),(codeParam, req.code))) onComplete {
              case Success(token) =>
                logger.debug(s"Token is: ${token.access_token}")
                http.endpointGetBearer[AccountResponse](s"$trulayerApi$accountsEndpoint", token.access_token) onComplete {
                  case Success(accounts) =>
                    refSize += senderRef -> accounts.results.size
                    refAccounts += senderRef -> Seq.empty
                    accounts.results.foreach(acc => getBalanceAndUpdateMap(acc, token.access_token, token.refresh_token, senderRef, accounts.results.size))
                  case Failure(t) =>
                    logger.error(s"Failed to get accounts", t)
                    senderRef ! RedirectResponse(Nil, error = Some(s"Failed to get accounts: $t"))
                }
                val teniiId = UserUtil.newUsers.headOption.getOrElse(s"tenii-${UUID.randomUUID().toString}")
                saveToken(token, None, teniiId)
                UserUtil.newUsers -= teniiId
                logger.debug(s"New users currently: ${UserUtil.newUsers}")
              case Failure(t) =>
                logger.error(s"Failed to get access token", t)
                senderRef ! RedirectResponse(Nil, error = Some(s"Failed to get access token: $t"))
            }
          } else {
            senderRef ! RedirectResponse(Nil, error = Some("Required permissions not given"))
          }
      }
    case req: TransactionRequest =>
      val senderRef = sender()
      implicit val timeout2: FiniteDuration = 20.seconds
      http.endpointGetBearer[TrulayerTransactionsResponse](s"$trulayerApi$accountsEndpoint/${req.accountId}$transactionsEndpoint", req.token) onComplete {
        case Success(trans) =>
          //logger.debug(s"Response from trulayer transactions is $trans")
         senderRef ! TransactionsResponse(trans.results.getOrElse(Nil))
          //TODO Send to payments api to add to pot
        case Failure(t) =>
          logger.error(s"Failed to get transactions", t)
          senderRef ! TransactionsResponse(Nil, error = Some(s"Failed to get transaction: $t"))
      }
    case req: TeniiTokenRequest =>
      val senderRef = sender()
      loadUser(req.teniiId) onComplete {
        case Success(userOpt) => userOpt match {
          case Some(user) => updateAccessToken(user.refresh) onComplete {
            case Success(newToken) =>
              senderRef ! TeniiTokenResponse(req.teniiId, Some(newToken.access_token))
              saveToken(newToken, Some(user), req.teniiId)
            case Failure(t) => logger.error(s"Unable to get new token for request: $req, investigate", t)
              senderRef ! TeniiTokenResponse(req.teniiId, Some("failure"), Some(s"Could not find a user for request: $req"))
          }
          case None => logger.error(s"User for request: $req not found, investigate")
            senderRef ! TeniiTokenResponse(req.teniiId, Some("fake"), Some(s"Could not find a user for request: $req"))
        }
        case Failure(t) => logger.error(s"Failure during lookup for user on request: $req", t)
          senderRef ! TeniiTokenResponse(req.teniiId, None, Some(s"Failure during lookup for user on request: $req"))
      }
    case other => logger.error(s"Unknown message received: $other")
  }

  def updateAccessToken(refreshToken: String) = {
    implicit val timeout2: FiniteDuration = 10.seconds
    val url = s"$trulayerUrl$tokenEndpoint"
    http.postAsForm[AccessTokenInfo](s"$url",Seq(("grant_type","refresh_token"),(clientIdParam,clientId),(clientSecretParam, clientSecret),(redirectParam, redirectUrl),(refreshParam, refreshToken)))
  }

  def saveToken(token: AccessTokenInfo, oldToken: Option[UserToken], tenii: String) = {
    val dbToken = oldToken.getOrElse(UserToken(teniiId = tenii, access = "", refresh = ""))
    Future {
      connection.save(dbToken.copy(access = token.access_token, refresh = token.refresh_token))
    } onComplete {
      case Success(res) => logger.debug(s"Upserted token $token, result is $res")
      case Failure(t) => logger.error(s"Failed to save token to database", t)
    }
  }

  def loadUser(teniiId: String) = {
    Future {
      connection.findByTeniiId(teniiId)
    }
  }

  def getBalanceAndUpdateMap(account: Account, token: String, refreshToken: String, actorRef: ActorRef, size: Int): Unit  = {
    implicit val timeout: FiniteDuration = 5.seconds
    http.endpointGetBearer[AccountBalances](s"$trulayerApi$accountsEndpoint/${account.account_id}$balance", token) onComplete {
      case Success(res) => logger.debug(s"Account balance returned is: ${res.results.headOption.map(_.current).getOrElse(0)}")
        createAccountAndSend(res.results.headOption.map(_.current).getOrElse(0.0))
      case Failure(t) => logger.error(s"Error thrown when attempting to get account with id: ${account.account_id}", t)
        createAccountAndSend(0.0)
    }

    def createAccountAndSend(balance: Double): Unit = {
      val redirectAccount = toRedirectAccount(account, balance)
      val seq : Seq[RedirectAccount] = refAccounts.getOrElse(actorRef, Seq.empty).:+(redirectAccount)
      refAccounts += actorRef -> seq
      logger.debug(s"Size of seq is: ${seq.size}")
      logger.debug(s"Expected size is: ${refSize.getOrElse(actorRef, 0)}")
      if(refSize.getOrElse(actorRef, 0) == seq.size) {
        val res = refAccounts.getOrElse(actorRef, Nil)
        actorRef ! RedirectResponse(res.toList, token, refreshToken)
        refSize.-=(actorRef)
        refAccounts.-=(actorRef)
        ()
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
  val transactionsEndpoint = "/transactions"
  val balance = "/balance"
  val clientIdParam = "client_id"
  val clientSecretParam = "client_secret"
  val redirectParam = "redirect_uri"
  val codeParam = "code"
  val refreshParam = "refresh_token"
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

  implicit def onSuccessDecodingError[TellerResponse](decodingError: io.circe.Error): TellerResponse = throw new Exception(s"Error decoding trulayer upstream response: $decodingError")
  implicit def onErrorDecodingError[TellerResponse](decodingError: String): TellerResponse = throw new Exception(s"Error decoding upstream error response: $decodingError")
  implicit def onError[TellerResponse](error: TrulayerErrors): TellerResponse = throw new Exception(s"Error thrown by Trulayer: ${error.results.headOption.map(_.error.getOrElse(""))}, description: ${error.results.headOption.map(_.error_description.getOrElse(""))}")
}