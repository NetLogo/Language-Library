package org.nlogo.langextension

class TargetLanguageErrorException(message: String, longMessage: String) extends Exception(message) {
  initCause(new Exception(longMessage))
  def getLongMessage: String = longMessage
}
