package com.ogun.tenii.trulayer.db

import com.mongodb.casbah.Imports._
import com.ogun.tenii.trulayer.model.db.UserToken
import com.typesafe.scalalogging.LazyLogging

class UserTokenConnection extends ObjectMongoConnection[UserToken] with LazyLogging {

  val collection = "tokens"

  override def transform(obj: UserToken): MongoDBObject = {
    MongoDBObject("_id" -> obj.id, "teniiId" -> obj.teniiId, "access" -> obj.access, "refresh" -> obj.refresh)
  }

  def findByTeniiId(name: String): Option[UserToken] = {
    findByProperty("teniiId", name, s"No user found with teniiId: $name")
  }

  def findById(id: String): Option[UserToken] = findByObjectId(id, s"No user token found with id: $id")

  def findByAccessToken(token: String): Option[UserToken] = {
    findByProperty("access", token, s"No user found with token: $token")
  }

  override def revert(obj: MongoDBObject): UserToken = {
    UserToken(
      Some(getObjectId(obj, "_id")),
      getString(obj, "teniiId"),
      getString(obj, "access"),
      getString(obj, "refresh")
    )
  }
}
