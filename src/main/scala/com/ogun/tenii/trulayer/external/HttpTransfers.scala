package com.ogun.tenii.trulayer.external

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.ogun.tenii.trulayer.model.TrulayerErrors
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.circe._
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

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
      .contentType(ContentTypes.`application/json`.toString())
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

  def endpointGetBearer[U](endpoint: String, token: String)(implicit timeout: FiniteDuration, decoder: Decoder[U], onSuccess: U => U, onSuccessDecodingError: io.circe.Error => U, onErrorDecodingError: String => U): Future[U] = {
    sttp
      .auth.bearer(token)
      .get(uri"$endpoint")
      .readTimeout(timeout)
      .contentType(ContentTypes.`application/json`.toString())
      .response(asJson[U])
      .send()
      .map(processResponse(_)(onSuccess, onSuccessDecodingError, onErrorDecodingError))
  }

  def postAsForm[U](endpoint: String, params: Seq[(String, String)])(implicit timeout: FiniteDuration, decoder: Decoder[U], onSuccess: U => U,
    onErrorDecodingError: String => U, mat: ActorMaterializer, unmarshal: Unmarshaller[HttpResponse, U]): Future[U] = {
    Http().singleRequest(HttpRequest(
      uri = akka.http.scaladsl.model.Uri.apply(endpoint),
      method = HttpMethods.POST,
      entity = akka.http.scaladsl.model.FormData(params.toMap).toEntity(HttpCharsets.`UTF-8`),
      protocol = HttpProtocols.`HTTP/1.1`
    )).map {
      resp =>
        resp.status.intValue() match {
          case 200 => onSuccess(Await.result(Unmarshal(resp).to[U], 1.seconds))
          case other =>
            resp.entity.discardBytes()
            onErrorDecodingError(s"upstream response: http code: $other")
        }
    }
  }

  private def processResponse[T](res: Response[Either[Error, T]])(implicit onSuccess: T => T, onSuccessDecodingError: io.circe.Error => T, onErrorDecodingError: String => T): T = {
    res.body match {
      case Right(Right(response)) => onSuccess(response)
      case Right(Left(decodingError)) =>
        logger.error(s"Error decoding upstream response: $decodingError")
        onSuccessDecodingError(decodingError)
      case Left(errorMsg) =>
        io.circe.parser.decode[TrulayerErrors](errorMsg) match {
          case Right(resp) => onSuccessDecodingError(resp)
          case Left(decodingError) =>
            logger.error(s"upstream response: http code: ${res.code}, message: $errorMsg")
            onErrorDecodingError(decodingError, errorMsg)
        }

    }
  }
}
