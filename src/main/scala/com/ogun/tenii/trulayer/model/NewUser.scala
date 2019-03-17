package com.ogun.tenii.trulayer.model

case class NewUser(teniiId: String)

case class UserWithProvider(teniiId: String, provider: String = "Mock")

case class Status(status: String = "OK")