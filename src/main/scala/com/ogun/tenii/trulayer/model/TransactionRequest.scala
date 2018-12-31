package com.ogun.tenii.trulayer.model

case class TransactionRequest(accountId: String, token: String)

case class TrulayerTransactionsResponse(results: Option[List[Transaction]])

case class Transaction(transaction_id: Option[String], timestamp:	Option[String], description:	Option[String], merchant_name:	Option[String],
                       amount: Option[Double], currency: Option[String], transaction_type: Option[String])

case class TransactionsResponse(transactions: List[Transaction], error: Option[String] = None)

case class GetTransactionResponse(transactionIds: List[String], teniiId: String, date: Option[String])