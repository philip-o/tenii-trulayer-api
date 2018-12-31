package com.ogun.tenii.trulayer.helpers

object NumberHelper {
  def dateToNumber(date: String) = {
    date.split("T").head.replaceAll("-", "").toInt
  }
}
