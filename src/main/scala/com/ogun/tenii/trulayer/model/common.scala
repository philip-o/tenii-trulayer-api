package com.ogun.tenii.trulayer.model

case class Account(account_id: String, account_type: String, account_number: AccountNumbers, currency: String,
  displayName: String, provider: Provider)

case class Provider(display_name: String, logo_uri: String, provider_id: String)

case class AccountNumbers(number: String, sort_code: String)