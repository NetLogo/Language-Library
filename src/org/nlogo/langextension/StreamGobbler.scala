package org.nlogo.languagelibrary

import java.io.{ BufferedReader, InputStream, InputStreamReader }
import javax.swing.SwingUtilities

import org.nlogo.api.{ OutputDestination, Workspace }

// Adapted from this StackOverflow answer: https://stackoverflow.com/a/1732506
// -Jeremy B July 2023
class StreamGobbler(ws: Workspace, is: InputStream) extends Thread {
  override def run(): Unit = {
    val reader = new BufferedReader(new InputStreamReader(is))
    var line: String = reader.readLine()
    while (line != null) {
      output(line)
      line = reader.readLine()
    }
  }

  private def output(s: String): Unit = {
    Logger.current.logMany("Subprocess.output()") { Seq("s", s) }
    if (!ws.workspaceContext.workspaceGUI) {
      println(s)
    } else {
      SwingUtilities.invokeLater { () =>
        ws.outputObject(s, null, addNewline = true, readable = false, OutputDestination.Normal)
      }
    }
  }

}
