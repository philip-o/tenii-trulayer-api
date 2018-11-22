package com.ogun.tenii.trulayer.model

case class TrulayerErrors(results: TrulayerError)

case class TrulayerError(error: Option[String], error_description: Option[String])