package com.ogun.tenii.trulayer.db

import java.util.Date

import com.mongodb.casbah.Imports._
import com.ogun.tenii.trulayer.config.Settings
import com.typesafe.scalalogging.LazyLogging

object MongoFactory {
  private val DATABASE = Settings.database
  val uri = MongoClientURI(s"mongodb://${Settings.host}/$DATABASE")
  val mongoClient = MongoClient(uri)
  val db = mongoClient(DATABASE)

  def getCollection(collection: String) = db(collection)
}

trait ObjectMongoConnection[A] extends LazyLogging {

  val collection: String

  def save(obj: A) = {
    val coll = retrieveCollection
    val toPersist = transform(obj)
    coll.save(toPersist)
  }

  protected def retrieveCollection = {
    MongoFactory.getCollection(collection)
  }

  protected def findByProperty(name: String, value: String, error: String) = {
    retrieveCollection.findOne(MongoDBObject(name -> value)) match {
      case Some(data) => Some(revert(data))
      case _ =>
        logger.error(error)
        None
    }
  }

  protected def findByObjectId(value: String, error: String) = {
    retrieveCollection.findOne(MongoDBObject("_id" -> new ObjectId(value))) match {
      case Some(data) => Some(revert(data))
      case _ =>
        logger.error(error)
        None
    }
  }

  protected def findByProperty(name: String, value: Int, error: String) = {
    retrieveCollection.findOne(MongoDBObject(name -> value)) match {
      case Some(data) => Some(revert(data))
      case _ =>
        logger.error(error)
        None
    }
  }

  protected def findAllByProperty(name: String, value: String, error: String) = {
    val cursor = retrieveCollection.find(MongoDBObject(name -> value))
    if (cursor.isEmpty) {
      logger.error(error)
      Nil
    } else {
      cursor.map(c => revert(c)).toList
    }
  }

  protected def findAll(error: String) = {
    val cursor = retrieveCollection.find()
    if (cursor.isEmpty) {
      logger.error(error)
      Nil
    } else {
      cursor.map(c => revert(c)).toList
    }
  }

  protected def transform(obj: A): MongoDBObject

  protected def revert(obj: MongoDBObject): A

  protected def getInt(obj: MongoDBObject, name: String) = getVar(obj, name).getOrElse(0).asInstanceOf[Int]

  protected def getString(obj: MongoDBObject, name: String) = getVar(obj, name).getOrElse("").asInstanceOf[String]

  protected def getOptional[B](obj: MongoDBObject, name: String) = {
    val res = getVar(obj, name)
    if (res.get.isInstanceOf[String])
      res.asInstanceOf[Option[B]]
    else if (res.get.isInstanceOf[Option[B]]) {
      val r = res.asInstanceOf[Option[B]].get
      r.asInstanceOf[Option[B]]
    }
    else None
  }

  protected def getObjectId(obj: MongoDBObject, name: String) = getVar(obj, name).asInstanceOf[Option[ObjectId]].get

  protected def getBigDecimal(obj: MongoDBObject, name: String) = getVar(obj, name).getOrElse(BigDecimal(0)).asInstanceOf[BigDecimal]

  protected def getDouble(obj: MongoDBObject, name: String) = getVar(obj, name).getOrElse(0).asInstanceOf[Double]

  protected def getDate(obj: MongoDBObject, name: String) = getVar(obj, name).getOrElse(new Date).asInstanceOf[Date]

  protected def getBoolean(obj: MongoDBObject, name: String) = getVar(obj, name).getOrElse(false).asInstanceOf[Boolean]

  protected def getObject[B](obj: MongoDBObject, name: String) = getVar(obj, name).get.asInstanceOf[B]

  protected def getBasicDBList(obj: MongoDBObject, name: String) = getVar(obj, name).asInstanceOf[Option[BasicDBList]]

  protected def getLong(obj: MongoDBObject, name: String) = getVar(obj, name).getOrElse(0L).asInstanceOf[Long]

  protected def getList[B](obj: MongoDBObject, name: String) = {
    getVar(obj, name).getOrElse(Nil).asInstanceOf[BasicDBList].toList.asInstanceOf[List[B]]
  }

  private def getVar(obj: MongoDBObject, name: String) = obj.get(name)
}
