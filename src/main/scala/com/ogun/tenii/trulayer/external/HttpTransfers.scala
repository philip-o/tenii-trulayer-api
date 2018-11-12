package com.ogun.tenii.trulayer.external

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.circe._
import com.typesafe.scalalogging.LazyLogging
import io.circe._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class HttpTransfers(implicit system: ActorSystem) extends LazyLogging {

  implicit private val sttpBackend: SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(system)

  def endpoint[T, U](endpoint: String, requestBody: T)(implicit timeout: FiniteDuration, encoder: Encoder[T], decoder: Decoder[U], onSuccess: U => U, onSuccessDecodingError: io.circe.Error => U, onErrorDecodingError: String => U): Future[U] = {
    sttp
      .post(uri"$endpoint")
      .readTimeout(timeout)
      .contentType(ContentTypes.`application/json`.toString())
      .headers()
      .body(requestBody)
      .response(asJson[U])
      .send()
      .map(processResponse(_)(onSuccess, onSuccessDecodingError, onErrorDecodingError))
  }

  def endpointEmptyBody[U](endpoint: String)(implicit timeout: FiniteDuration, decoder: Decoder[U], onSuccess: U => U, onSuccessDecodingError: io.circe.Error => U, onErrorDecodingError: String => U): Future[U] = {
    sttp
      .post(uri"$endpoint")
      .readTimeout(timeout)
      .headers()
      .response(asJson[U])
      .send()
      .map(processResponse(_)(onSuccess, onSuccessDecodingError, onErrorDecodingError))
  }

  def endpointGet[U](endpoint: String, headers: (String, String)*)(implicit timeout: FiniteDuration, decoder: Decoder[U], onSuccess: U => U, onSuccessDecodingError: io.circe.Error => U, onErrorDecodingError: String => U): Future[U] = {
    sttp
      .get(uri"$endpoint")
      .readTimeout(timeout)
      .contentType(ContentTypes.`application/json`.toString())
      .headers(headers.toMap)
      .response(asJson[U])
      .send()
      .map(processResponse(_)(onSuccess, onSuccessDecodingError, onErrorDecodingError))
  }

  private def processResponse[T](res: Response[Either[Error, T]])(implicit onSuccess: T => T, onSuccessDecodingError: io.circe.Error => T, onErrorDecodingError: String => T): T = {
    res.body match {
      case Right(Right(response)) => onSuccess(response)
      case Right(Left(decodingError)) =>
        logger.error(s"Error decoding upstream response: $decodingError")
        onSuccessDecodingError(decodingError)
      case Left(errorMsg) =>
        logger.error(s"upstream response: http code: ${res.code}, message: $errorMsg")
        onErrorDecodingError(s"upstream response: http code: ${res.code}, message: $errorMsg")
    }
  }
}
