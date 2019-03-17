package com.ogun.tenii.trulayer.implicits

import com.ogun.tenii.trulayer.model.{Account, RedirectAccount, UserWithProvider}

trait AccountImplicits {

  def toRedirectAccount(account: Account, balance: Double) : RedirectAccount = {
    RedirectAccount(
      account.account_id,
      account.account_type,
      account.account_number,
      account.currency,
      account.provider,
      balance
    )
  }

  implicit def toUserAndProvider(teniiId: String, provider: String) : UserWithProvider = {
    UserWithProvider(
      teniiId,
      provider
    )
  }

  implicit def toUserAndProvider(idAndProvider: (String, String)) : UserWithProvider = {
    UserWithProvider(
      idAndProvider._1,
      idAndProvider._2
    )
  }
}
