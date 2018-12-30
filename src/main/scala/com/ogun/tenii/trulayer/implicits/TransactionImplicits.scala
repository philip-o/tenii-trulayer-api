package com.ogun.tenii.trulayer.implicits

import java.util.UUID

import com.ogun.tenii.trulayer.model.{ProcessTransactionRequest, Transaction}

trait TransactionImplicits {

  def toProcessTransactionRequest(transaction: Transaction, teniiId: String, accountId: String) = {
    ProcessTransactionRequest(
      transaction.transaction_id.getOrElse(UUID.randomUUID().toString),
      accountId,
      teniiId,
      transaction.amount.getOrElse(0),
      transaction.timestamp.getOrElse("")
    )
  }
}
