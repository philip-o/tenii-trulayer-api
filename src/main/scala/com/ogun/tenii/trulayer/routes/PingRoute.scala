package com.ogun.tenii.trulayer.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.pattern.CircuitBreaker
import com.typesafe.scalalogging.LazyLogging
import javax.ws.rs.Path

@Path("ping")
class PingRoute(implicit system: ActorSystem, breaker: CircuitBreaker) extends RequestDirectives with LazyLogging {

  def route: Route = pathPrefix("ping") {
    pingMe
  }

  def pingMe: Route = {
    get {
      complete("""{"Status": "OK"}""")
    }
  }
}
