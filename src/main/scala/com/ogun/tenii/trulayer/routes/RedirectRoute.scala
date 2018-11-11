package com.ogun.tenii.trulayer.routes

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.{CircuitBreaker, ask}
import akka.util.Timeout
import com.ogun.tenii.trulayer.actors.TrulayerActor
import com.ogun.tenii.trulayer.model.{Redirect, RedirectResponse}
import com.typesafe.scalalogging.LazyLogging
import javax.ws.rs.Path
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@Path("/callback")
class RedirectRoute(implicit system: ActorSystem, breaker: CircuitBreaker) extends RequestDirectives with LazyLogging {

  implicit val executor: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(10.seconds)
  protected val trulayerActor: ActorRef = system.actorOf(Props(classOf[TrulayerActor]))

  def route: Route = pathPrefix("callback") {
    callback
  }

  def callback: Route = {
    get {
      (path("callback") & codeDirective & scopeDirective & stateDirective & errorDirective).as(Redirect) { request =>
        logger.info(s"GET /callback - $request")
        onCompleteWithBreaker(breaker)(trulayerActor ? request) {
          case Success(msg: RedirectResponse) if msg.error.nonEmpty => complete(StatusCodes.InternalServerError -> msg)
          case Success(msg: RedirectResponse) => complete(StatusCodes.OK -> msg)
          case Failure(t) => failWith(t)
        }
      }
    }
  }
}
