package com.ogun.tenii.trulayer.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.CircuitBreaker
import com.ogun.tenii.trulayer.helpers.UserUtil
import com.ogun.tenii.trulayer.model.{NewUser, UserWithProvider, Status}
import com.typesafe.scalalogging.LazyLogging
import javax.ws.rs.Path
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

@Path("newUser")
class UserRoute(implicit system: ActorSystem, breaker: CircuitBreaker) extends RequestDirectives with LazyLogging {

  def route: Route = pathPrefix("newUser") {
    addUser
  }

  def addUser: Route = {
    post {
      entity(as[UserWithProvider]) { request =>
        logger.info(s"POST /newUser - $request")
        UserUtil.userMap += request.teniiId -> request.provider
        complete(StatusCodes.OK -> Status())
      }
    }
  }
}

