package com.ogun.tenii.trulayer.routes

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.{ ask, CircuitBreaker }
import akka.util.Timeout
import com.ogun.tenii.trulayer.actors.TrulayerActor
import com.ogun.tenii.trulayer.model.{AccountsAndTokenResponse, TeniiLoginRequest}
import com.typesafe.scalalogging.LazyLogging
import javax.ws.rs.Path
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Path("/login")
class LoginRoute(implicit system: ActorSystem, breaker: CircuitBreaker) extends RequestDirectives with LazyLogging {

  implicit val executor: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(30.seconds)
  protected val trulayerActor: ActorRef = system.actorOf(Props(classOf[TrulayerActor]))

  def route: Route = pathPrefix("login") {
    login
  }

  def login: Route = {
    post {
      entity(as[TeniiLoginRequest]) { request =>
        logger.info(s"POST /login - $request")
        onCompleteWithBreaker(breaker)(trulayerActor ? request) {
          case Success(msg: AccountsAndTokenResponse) if msg.error.nonEmpty => complete(StatusCodes.InternalServerError -> msg)
          case Success(msg: AccountsAndTokenResponse) => complete(StatusCodes.OK -> msg)
          case Failure(t) => failWith(t)
        }
      }
    }
  }

}
