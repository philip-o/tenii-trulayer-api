package com.ogun.tenii.trulayer.model

case class AccountBalances(results: List[AccountBalance])

case class AccountBalance(currency: String, current: Double, update_timestamp: String)
