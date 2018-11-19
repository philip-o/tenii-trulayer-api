package com.ogun.tenii.trulayer.implicits

import com.ogun.tenii.trulayer.model.{Account, RedirectAccount}

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
}
