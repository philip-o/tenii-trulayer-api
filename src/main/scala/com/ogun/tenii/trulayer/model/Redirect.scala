package com.ogun.tenii.trulayer.model

case class Redirect(code: String, scope: String, state: Option[String], error: Option[String])

case class RedirectAccount(account_id: String, account_type: String, account_number: AccountNumbers, currency: String, provider: Provider, balance: Double)

case class RedirectResponse(accounts: List[RedirectAccount], accessToken: String = "", error: Option[String] = None)

case class TrulayerPermissions(permission: String, perm: Boolean = true)

