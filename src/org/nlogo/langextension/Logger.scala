package org.nlogo.languagelibrary

class Logger {
  private var _debugEnabled = false

  def enableDebug(): Unit = {
    this._debugEnabled = true
  }

  def isDebugEnabled: Boolean = {
    this._debugEnabled
  }

  def logOne(source: String) = {
    if (isDebugEnabled) {
      println(source)
    }
  }

  def logMany(source: String)(getMessages: => Seq[String]) = {
    if (isDebugEnabled) {
      println(s"$source: ${getMessages.mkString(" ")}")
    }
  }
}
