package com.ogun.tenii.trulayer.model

case class TeniiTokenRequest(teniiId: String)

case class TeniiTokenResponse(teniiId: String, token: Option[String], error: Option[String] = None)
