package com.ogun.tenii.trulayer.routes

import akka.http.scaladsl.server.{Directive1, Directives, PathMatcher1}

trait RequestDirectives extends Directives {

  val codeDirective: Directive1[String] = parameter("code")
  val scopeDirective: Directive1[String] = parameter("scope")
  val stateDirective: Directive1[Option[String]] = parameter("state".?)
  val errorDirective: Directive1[Option[String]] = parameter("error".?)
  val accountSegment: PathMatcher1[String] = Segment

  val tokenDirective: Directive1[String] = headerValueByName("token")

}
