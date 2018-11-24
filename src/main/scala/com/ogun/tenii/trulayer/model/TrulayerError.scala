package com.ogun.tenii.trulayer.model

case class TrulayerErrors(results: List[TrulayerError])

case class TrulayerError(error: Option[String], error_description: Option[String])