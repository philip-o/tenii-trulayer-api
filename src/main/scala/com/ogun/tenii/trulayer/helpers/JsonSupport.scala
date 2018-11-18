package com.ogun.tenii.trulayer.helpers

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.ogun.tenii.trulayer.model.AccessTokenInfo
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat4(AccessTokenInfo)
}
