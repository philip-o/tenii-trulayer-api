package com.ogun.tenii.trulayer.implicits

import java.util.UUID

import com.ogun.tenii.trulayer.model.{ProcessTransactionRequest, Transaction}

trait TransactionImplicits {

  private def randomUUID = UUID.randomUUID().toString

  def toProcessTransactionRequest(transaction: Transaction, teniiId: String) = {
    ProcessTransactionRequest(
      transaction.transaction_id.getOrElse(randomUUID),
      randomUUID,
      teniiId,
      transaction.amount.getOrElse(0),
      transaction.timestamp.getOrElse("")
    )
  }
}
