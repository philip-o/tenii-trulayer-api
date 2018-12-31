package com.ogun.tenii.trulayer.actors

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import com.ogun.tenii.trulayer.db.UserTokenConnection
import com.ogun.tenii.trulayer.external.{HttpTransfers, ProductsEndpoints}
import com.ogun.tenii.trulayer.model._
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Properties, Success}
import com.ogun.tenii.trulayer.helpers.{JsonSupport, NumberHelper, UserUtil}
import com.ogun.tenii.trulayer.implicits.{AccountImplicits, TransactionImplicits}
import com.ogun.tenii.trulayer.model.db.UserToken

import scala.collection.mutable
import scala.concurrent.Future

class TrulayerActor extends Actor
  with LazyLogging
  with TrulayerEndpoint
  with JsonSupport
  with AccountImplicits
  with ProductsEndpoints
  with TransactionImplicits {

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
          senderRef ! AccountsAndTokenResponse(Nil, error = Some(s"Error from trulayer: $err"))
        case None =>
          if (validatePermissions(providedPermissions = providedPermissions(req.scope))) {
            implicit val timeout2: FiniteDuration = 20.seconds
            retrieveAccountsWithRedirect(req, senderRef)
          } else {
            senderRef ! AccountsAndTokenResponse(Nil, error = Some("Required permissions not given"))
          }
      }
    case req: TransactionRequest =>
      implicit val timeout2: FiniteDuration = 20.seconds
      retrieveTransactions(req, sender())
    case req: TeniiLoginRequest =>
      val senderRef = sender()
      loadUser(req.teniiId) onComplete {
        case Success(userOpt) => userOpt match {
          case Some(user) => updateAccessToken(user.refresh) onComplete {
            case Success(newToken) =>
              implicit val timeout2: FiniteDuration = 20.seconds
              retrieveAccounts(newToken.access_token, newToken.refresh_token, senderRef)
              saveToken(newToken, Some(user), req.teniiId)
            case Failure(t) => logger.error(s"Unable to get new token for request: $req, investigate", t)
              senderRef ! AccountsAndTokenResponse(Nil, error = Some(s"Could not find a user for request: $req"))
          }
          case None => logger.error(s"User for request: $req not found, investigate")
            senderRef ! AccountsAndTokenResponse(Nil, error = Some(s"Could not find a user for request: $req"))
        }
        case Failure(t) => logger.error(s"Failure during lookup for user on request: $req", t)
          senderRef ! AccountsAndTokenResponse(Nil, error = Some(s"Failure during lookup for user on request: $req"))
      }
    case other => logger.error(s"Unknown message received: $other")
  }

  private def retrieveAccountsWithRedirect(req: Redirect, senderRef: ActorRef)(implicit duration: FiniteDuration) = {
    val url = s"$trulayerUrl$tokenEndpoint"
    val query = s"&grant_type=authorization_code&$clientIdParam$clientId&$clientSecretParam$clientSecret&$redirectParam$redirectUrl&$codeParam${req.code}"
    logger.info(s"url is $url$query")
    http.postAsForm[AccessTokenInfo](s"$url", Seq(("grant_type", "authorization_code"), (clientIdParam, clientId), (clientSecretParam, clientSecret), (redirectParam, redirectUrl), (codeParam, req.code))) onComplete {
      case Success(token) =>
        logger.debug(s"Token is: ${token.access_token}")
        retrieveAccounts(token.access_token, token.refresh_token, senderRef)
        val teniiId = UserUtil.newUsers.headOption.getOrElse(s"tenii-${UUID.randomUUID().toString}")
        saveToken(token, None, teniiId)
        UserUtil.newUsers -= teniiId
        logger.debug(s"New users currently: ${UserUtil.newUsers}")
      case Failure(t) =>
        logger.error(s"Failed to get access token", t)
        senderRef ! AccountsAndTokenResponse(Nil, error = Some(s"Failed to get access token: $t"))
    }
  }

  private def retrieveAccounts(accessToken: String, refreshToken: String, senderRef: ActorRef)(implicit duration: FiniteDuration) = {
    http.endpointGetBearer[AccountResponse](s"$trulayerApi$accountsEndpoint", accessToken) onComplete {
      case Success(accounts) =>
        refSize += senderRef -> accounts.results.size
        refAccounts += senderRef -> Seq.empty
        accounts.results.foreach(acc => getBalanceAndUpdateMap(acc, accessToken, refreshToken, senderRef, accounts.results.size))
      case Failure(t) =>
        logger.error(s"Failed to get accounts", t)
        senderRef ! AccountsAndTokenResponse(Nil, error = Some(s"Failed to get accounts: $t"))
    }
  }

  private def retrieveTransactions(req: TransactionRequest, senderRef: ActorRef)(implicit duration: FiniteDuration) = {
    //TODO load token and pass into request
    Future {
      connection.findByTeniiId(req.token)
    } onComplete {
      case Success(tokenOpt) => tokenOpt match {
        case Some(token) => http.endpointGetBearer[TrulayerTransactionsResponse](s"$trulayerApi$accountsEndpoint/${req.accountId}$transactionsEndpoint", token.access) onComplete {
          case Success(trans) =>
            //logger.debug(s"Response from trulayer transactions is $trans")
            senderRef ! TransactionsResponse(trans.results.getOrElse(Nil))
            //TODO Check user has source bank account, compare account id
            if(trans.results.getOrElse(Nil).nonEmpty) {
              processTransactions(trans.results.get, req.token)
            }
          case Failure(t) => logger.error(s"Failed to get transactions", t)
            senderRef ! TransactionsResponse(Nil, error = Some(s"Failed to get transaction: $t"))
        }
        case None => logger.error(s"No token for user")
          senderRef ! TransactionsResponse(Nil, error = Some(s"No token for user"))
      }
      case Failure(t) => logger.error(s"Failed to load token for request: $req", t)
        senderRef ! TransactionsResponse(Nil, error = Some(s"Failed to load token for request: $req"))
    }
  }

  private def processTransactions(transactions: List[Transaction], teniiId: String) = {
    implicit val duration = 20.seconds
    http.endpointGet[SourceBankAccountResponse](s"$productsUrl$accountPath$teniiId") onComplete {
      case Success(response) => response.accountId match {
        case Some(accountId) => http.endpointGet[GetTransactionResponse](s"$productsUrl$transactionPath/$teniiId") onComplete {
          case Success(tranOpt) => tranOpt.transactionIds match {
            case Nil => loopThroughTransactions(transactions, accountId)
            case ids =>
              val transIdsAndDates = (ids, tranOpt.date.map(NumberHelper.dateToNumber).get)
              val partitioned = transactions.partition(tr => NumberHelper.dateToNumber(tr.timestamp.get) >= transIdsAndDates._2)
              val remaining = partitioned._1.filterNot(tran => ids.contains(tran.transaction_id.get))
              loopThroughTransactions(remaining, accountId)
//              val split = transactions.span(_.transaction_id.get == ids.last)
//              if(split._2.isEmpty)
//                loopThroughTransactions(transactions, accountId)
//              else
//                loopThroughTransactions(split._2, accountId)
          }
          case Failure(t) => logger.error(s"error thrown when getting last transaction, please run manually and then process for user: $teniiId", t)
        }
        case None => logger.error(s"No account setup, unable to process transactions")
      }
      case Failure(t) => logger.error(s"Failed to get account, unable to process transactions", t)
    }

    def loopThroughTransactions(toLoop: List[Transaction], accountId: String) = {

      for(transaction <- toLoop) {
        http.endpoint[ProcessTransactionRequest, ProcessTransactionResponse](s"$productsUrl$transactionPath", toProcessTransactionRequest(transaction, teniiId, accountId: String)) onComplete {
          case Success(_) => logger.debug(s"Processed transaction successfully: ${transaction.transaction_id}")
          case Failure(t) => logger.error(s"Failed to process transaction: ${transaction.transaction_id}", t)
        }
        //TODO Change logic to remove sleep
        Thread.sleep(2000)
      }

//      toLoop.foreach {
//        transaction => http.endpoint[ProcessTransactionRequest, ProcessTransactionResponse](s"$productsUrl$transactionPath", toProcessTransactionRequest(transaction, teniiId)) onComplete {
//          case Success(_) => logger.debug(s"Processed transaction successfully: ${transaction.transaction_id}")
//          case Failure(t) => logger.error(s"Failed to process transaction: ${transaction.transaction_id}", t)
//        }
//      }
    }
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
        actorRef ! AccountsAndTokenResponse(res.toList, token, refreshToken)
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