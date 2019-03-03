package com.ogun.tenii.trulayer.external

import com.ogun.tenii.trulayer.config.Settings

trait ProductsEndpoints {
  val productsUrl = Settings.productsHost
  val accountPath = Settings.bankAccountPath
  val transactionPath = Settings.transactionPath
}
