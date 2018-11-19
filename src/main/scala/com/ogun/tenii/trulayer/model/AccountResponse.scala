package com.ogun.tenii.trulayer.model

case class Account(account_id: String, account_type: String, account_number: AccountNumbers, currency: String, provider: Provider)

case class AccountResponse(results: List[Account])