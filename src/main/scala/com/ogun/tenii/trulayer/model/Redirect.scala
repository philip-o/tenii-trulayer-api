package com.ogun.tenii.trulayer.model

case class Redirect(code: String, scope: String, state: Option[String], error: Option[String])

case class RedirectResponse(accounts: List[Account], accessToken: String = "", error: Option[String] = None)

case class TrulayerPermissions(permission: String, perm: Boolean = true)

