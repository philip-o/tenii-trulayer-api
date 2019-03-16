package com.ogun.tenii.trulayer.config

import com.typesafe.config.{Config, ConfigFactory}


object Settings {

  private[config] val config: Config = ConfigFactory.load()

  val database = config.getStringList("mongo.database").get(0)
  val host = config.getStringList("mongo.host").get(0)

  val productsHost = config.getStringList("tenii.products.endpoint").get(0)
  val bankAccountPath = config.getStringList("tenii.products.accountPath").get(0)
  val transactionPath = config.getStringList("tenii.products.transactionPath").get(0)

  val accountsPath = config.getStringList("trulayer.accountsPath").get(0)
  val trulayerTransactionsPath = config.getStringList("trulayer.transactionsPath").get(0)
  val trulayerBalancePath = config.getStringList("trulayer.balancePath").get(0)

  val tokenPath = config.getStringList("trulayer.tokenPath").get(0)
  val trulayerApiEndpoint = config.getStringList("trulayer.apiEndpoint").get(0)
  val trulayerAuthURL = config.getStringList("trulayer.authEndpoint").get(0)
  val trulayerClientId = config.getStringList("trulayer.clientId").get(0)
  val trulayerClientSecret = config.getStringList("trulayer.clientSecret").get(0)
}
