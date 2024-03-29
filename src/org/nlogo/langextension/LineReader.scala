package org.nlogo.languagelibrary

import java.io.{ Closeable, InputStreamReader }
import java.lang.AutoCloseable

class LineReader(in: InputStreamReader) extends Closeable with AutoCloseable {

  private val bufferSize = 8096
  private val buffer = new Array[Char](bufferSize)
  private val response = new StringBuilder()

  def close(): Unit = {
    this.in.close()
  }

  def readLine(): String = {
    Logger.current.logOne("LineReader.readLine()")
    response.clear()
    var readCount = in.read(buffer, 0, bufferSize)
    if (readCount != -1) { response.appendAll(buffer, 0, readCount) }
    Logger.current.logMany("LineReader.readLine()") { Seq("first read", "readCount", readCount.toString, "response", response.toString) }

    while (readCount != -1 && response.last != '\n') {
      readCount = in.read(buffer, 0, bufferSize)
      if (readCount != -1) { response.appendAll(buffer, 0, readCount) }
      Logger.current.logMany("LineReader.readLine()") { Seq("loop read", "readCount", readCount.toString, "response", response.toString) }
    }

    if (response.length == 0) {
      null
    } else {
      response.toString()
    }
  }

}
