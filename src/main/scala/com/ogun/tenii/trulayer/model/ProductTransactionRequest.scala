package com.ogun.tenii.trulayer.model

case class ProcessTransactionRequest(transactionId: String, accountId: String, teniiId: String, amount: Double, date: String)

case class ProcessTransactionResponse(transactionId: String, error: Option[String] = None)