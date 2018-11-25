package com.ogun.tenii.trulayer.model

case class TransactionRequest(accountId: String, token: String)

case class TrulayerTransactionsResponse(results: Option[List[Transaction]])

case class Transaction(transaction_id: Option[String], timestamp:	Option[String], description:	String, merchant_name:	String,
                       amount: Double, currency: String, transaction_type: String)

case class TransactionsResponse(transactions: List[Transaction], error: Option[String] = None)