package com.ogun.tenii.trulayer.model.db

import org.bson.types.ObjectId

case class UserToken(id: Option[ObjectId] = None, teniiId: String, access: String, refresh: String, provider: String)