package org.nlogo.languagelibrary

import org.json4s.JValue
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{ compact, parse, render }

import org.nlogo.api.Exceptions.ignoring
import org.nlogo.api.{ ExtensionException, OutputDestination, Workspace }
import org.nlogo.core.Syntax
import org.nlogo.nvm.HaltException
import org.nlogo.workspace.AbstractWorkspace
import org.nlogo.languagelibrary.config.Platform

import java.io._
import java.lang.ProcessBuilder.Redirect
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ ExecutorService, Executors, TimeUnit }
import javax.swing.SwingUtilities
import scala.collection.JavaConverters._
import scala.concurrent.SyncVar
import scala.concurrent.duration.{ Duration, DurationInt }
import scala.util.{ Failure, Success, Try }

/**
 * A Subprocess manages the system subprocess running the target language code and the network socket that carries
 * messages between the extension code and the target language code.
 */
object Subprocess {
  //-------------------------Constants----------------------------------------
  object OutTypes {
    val quitMsg = -1
    val stmtMsg = 0
    val exprMsg = 1
    val assnMsg = 2
    val exprStringifiedMsg = 3
    val heartbeatRequestMsg = 4
  }

  object InTypes {
    val successMsg = 0
    val errorMsg = 1
    val hearbeatResponseMsg = 4
  }

  val logger = new Logger()

  // All of the things that can be converted into Json to be passed to the target language code
  val convertibleTypesSyntax: Int = Syntax.AgentType | Syntax.AgentsetType | Syntax.ReadableType

  //-------------------------Public Methods------------------------------------
  /**
   * Create and start a new subprocess
   *
   * @param ws                The NetLogo workspace
   * @param processStartCmd   The command used to start the subprocess. e.g. for the python extension,
   *                          this is the path to the chosen python binary
   * @param processStartArgs  Any args that need to be passed in to the shell command
   * @param extensionName     The name of the extension. e.g "py" for the python extension
   * @param extensionLongName A longer, more readable name for the extension. e.g. "Python" for the python extension
   * @param suppliedPort      If your process does not find its own port to use (and send it as the first line out of its
   *                          stdout) then you need to specify a port to use here.
   * @return A new subprocess
   */
  def start(ws: Workspace,
            processStartCmd: Seq[String],
            processStartArgs: Seq[String],
            extensionName: String,
            extensionLongName: String,
            suppliedPort: Option[Int] = None
           ): Subprocess = {

    Subprocess.logger.logOne("Subprocess.start()")

    val workingDirectory: File = getWorkingDirectory(ws)

    val proc = new ProcessBuilder(createSystemCommandTokens(processStartCmd ++ processStartArgs).asJava)
      .directory(workingDirectory)
      .start()

    val port: Int = choosePort(suppliedPort, proc)

    val socket = makeSocketConnection(proc, port)

    if (!proc.isAlive) {
      earlyFail(proc, s"Process terminated early.")
    }

    new Subprocess(ws, proc, socket, extensionName, extensionLongName)
  }

  /**
   * Get the PATH for the system
   *
   * @return the PATH
   */
  def path: Seq[File] = {
    val basePath = sys.env.getOrElse("PATH", "")
    val os = System.getProperty("os.name").toLowerCase

    val unsplitPath = if (os.contains("mac") && basePath == "/usr/bin:/bin:/usr/sbin:/sbin")
    // On MacOS, .app files are executed with a neutered PATH environment variable.
    // So, we check if we're on MacOS and if we have that neuteredPATH. If so, we want to execute with the users
    // actual PATH. We use `path_helper` to get that. It's not perfect; it will miss PATHs defined in certain files,
    // but hopefully it's good enough.
      getSysCmdOutput("/bin/bash", "-l", "-c", "echo $PATH").head + basePath
    else
      basePath
    unsplitPath.split(File.pathSeparatorChar).map(new File(_)).filter(f => f.isDirectory)
  }

  //-------------------------Private Methods-----------------------------------
  /**
   * If specifiedPort is non-zero, use it, otherwise read from the process' first line of stdout and use that as
   * the port
   * @param suppliedPort the port to be used, if non-zero
   * @param proc the actual subprocess
   * @return
   */
  private def choosePort(suppliedPort: Option[Int], proc: Process) = {
    suppliedPort.getOrElse({
      val pbInput = new BufferedReader(new InputStreamReader(proc.getInputStream))
      extractPortFromProc(proc, pbInput)
    })
  }

  private def extractPortFromProc[A](proc: Process, pbInput: BufferedReader): Int = {
    val portLine = pbInput.readLine
    val portLineInt = try {
      portLine.toInt
    } catch {
      case _: NumberFormatException =>
        earlyFail(proc, s"Process did not provide expected output. Expected a valid port number but got:\n$portLine")
    }

    if (portLineInt <= 0 || portLineInt > 65535) {
      earlyFail(proc, s"Process did not provide expected output. Expected a valid port number but got:\n$portLine")
    }

    portLineInt
  }

  private def getWorkingDirectory(ws: Workspace) = {
    val prefix = new File(ws.asInstanceOf[AbstractWorkspace].fileManager.prefix)
    if (prefix.exists) {
      prefix
    } else {
      new File(System.getProperty("user.home"))
    }
  }

  private def makeSocketConnection(proc: Process, port: Int): Socket = {
    var socket: Socket = null
    while (socket == null && proc.isAlive) {
      try {
        socket = new Socket("localhost", port)
      } catch {
        case _: IOException => // Keep trying
        case e: SecurityException => throw new ExtensionException(e)
      }
    }
    socket
  }

  private def earlyFail(proc: Process, prefix: String): Nothing = {
    val stdout = readAllReady(new InputStreamReader(proc.getInputStream))
    val stderr = readAllReady(new InputStreamReader(proc.getErrorStream))
    val msg = (stderr, stdout) match {
      case ("", s) => s
      case (s, "") => s
      case (e, o) => s"Error output:\n$e\n\nOutput:\n$o"
    }
    throw new ExtensionException(s"$prefix\n$msg")
  }

  private def readAllReady(in: InputStreamReader): String = {
    val sb = new StringBuilder
    while (in.ready) {
      sb.append(in.read().toChar)
    }
    sb.toString
  }

  private def createSystemCommandTokens(args: Seq[String]): Seq[String] = {
    val os = System.getProperty("os.name").toLowerCase
    if (os.contains("mac"))
      List("/bin/bash", "-l", "-c", args.map(a => s"'$a'").mkString(" "))
    else
      args
  }

  private def getSysCmdOutput(cmd: String*): List[String] = {
    val proc = new ProcessBuilder(cmd: _*).redirectError(Redirect.PIPE).redirectInput(Redirect.PIPE).start()
    val in = new BufferedReader(new InputStreamReader(proc.getInputStream))
    Iterator.continually(in.readLine()).takeWhile(_ != null).toList
  }

}

class Subprocess(ws: Workspace, proc: Process, socket: Socket, extensionName: String, extensionLongName: String) {
  //---------------------------Class Variables--------------------------------
  private val shuttingDown = new AtomicBoolean(false)
  private val isRunningLegitJob = new AtomicBoolean(false)
  val convert = new Convert(extensionLongName)

  val inReader = new LineReader(new InputStreamReader(socket.getInputStream))

  val out = new BufferedOutputStream(socket.getOutputStream)

  val stdout = new InputStreamReader(proc.getInputStream)
  val stderr = new InputStreamReader(proc.getErrorStream)

  private val executor: ExecutorService = Executors.newSingleThreadExecutor()

  //---------------------------Public Methods--------------------------------
  /**
   * Send an "exec" commmand to the subproccess with the given statement
   *
   * @param stmt the statement to give
   * @return The message passed back, converted to a NetLogo object
   */
  def exec(stmt: String): AnyRef = {
    Subprocess.logger.logMany("Subprocess.exec()") { Seq("stmt", stmt) }
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendStmt(stmt))
        })
      }
    }.get.get
  }

  /**
   * Send an "eval" commmand to the subproccess with the given expression
   *
   * @param expr the expression to evaluate
   * @return The message passed back, converted to a NetLogo object
   */
  def eval(expr: String): AnyRef = {
    Subprocess.logger.logMany("Subprocess.eval()") { Seq("expr", expr) }
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendExpr(expr))
        })
      }
    }.get.get
  }

  /**
   * Send an "evalStringified" command to the subproccess with the given expression. Used to create a pop-out
   * interpreter
   *
   * @param expr the expression to evaluate
   * @return The stringified result
   */
  def evalStringified(expr: String): String = {
    Subprocess.logger.logMany("Subprocess.evalStringified()") { Seq("expr", expr) }
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendExprStringified(expr))
        })
      }
    }.get.get.toString
  }

  /**
   * Send an "assign" command to the subprocess with the given variable name (for the target environment) and value (a
   * NetLogo object).
   *
   * @param varName The variable name
   * @param value the value
   * @return
   */
  def assign(varName: String, value: AnyRef): Unit = {
    Subprocess.logger.logMany("Subprocess.assign()") { Seq("varName", varName, ", value", value.toString) }
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendAssn(varName, value))
        })
      }
    }.get.get
  }

  /**
   * Send a generic message to the subprocces, which it can interpret however it wishes, along with a body as JSON.
   *
   * @param msg_type An integer representing the type of message to be sent
   * @param value    a value to send
   * @return the value sent back from the subprocess
   */
  def genericJson(msg_type: Int, value: JValue): AnyRef = {
    Subprocess.logger.logMany("Subprocess.genericJson()") { Seq("msg_type", msg_type.toString, ", value", value.toString) }
    generic(msg_type, value)
  }

  /**
   * Send a generic message to the subprocces, which it can interpret however it wishes, along with a body
   *
   * @param msg_type An integer representing the type of message to be sent
   * @param value    a value to send
   * @return The value sent back from the subprocess
   */
  def generic(msg_type: Int, value: AnyRef): AnyRef = {
    Subprocess.logger.logMany("Subprocess.generic()") { Seq("msg_type", msg_type.toString, ", value", value.toString) }
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendGeneric(msg_type, value))
        })
      }
    }.get.get
  }

  /**
   * Shut down the subprocess.
   */
  def close(): Unit = {
    Subprocess.logger.logOne("Subprocess.close()")
    quit()
    shuttingDown.set(true)
    executor.shutdownNow()
    ignoring(classOf[IOException])(inReader.close())
    ignoring(classOf[IOException])(out.close())
    ignoring(classOf[IOException])(socket.close())
    proc.destroyForcibly()
    proc.waitFor(3, TimeUnit.SECONDS)
    if (proc.isAlive)
      throw new ExtensionException(s"${extensionLongName} process failed to shutdown. Please shut it down via your process manager")
  }

  //---------------------------Private Utilities-------------------------------
  private def quit(): Unit = {
    Subprocess.logger.logOne("Subprocess.quit()")
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendQuit())
        })
      }
    }.get.get
  }

  /**
   * Output to the command center (if not headless) or stdout (if headless)
   * @param s
   */
  private def output(s: String): Unit = {
    Subprocess.logger.logMany("Subprocess.output()") { Seq("s", s) }
    if (ws.isHeadless || Platform.isHeadless)
      println(s)
    else
      SwingUtilities.invokeLater { () =>
        ws.outputObject(s, null, addNewline = true, readable = false, OutputDestination.Normal)
      }
  }

  /**
   * Send subprocess' stdout to the command center if not headless
   */
  private def redirectPipes(): Unit = {
    val stdoutContents = Subprocess.readAllReady(stdout)
    val stderrContents = Subprocess.readAllReady(stderr)
    if (stdoutContents.nonEmpty)
      output(stdoutContents)
    if (stderrContents.nonEmpty)
      output(s"${extensionLongName} Error output:\n$stderrContents")
  }

  //--------------------Asynchronous message passing functionality-------------
  private def invalidateJobs(): Unit = isRunningLegitJob.set(false)

  private object Haltable {
    def apply[R](body: => R): R = {
      try {
        body
      } catch {
        case _: InterruptedException =>
          Thread.interrupted()
          invalidateJobs()
          throw new HaltException(true)
        case e: Throwable => throw e
      }
    }
  }

  private object HandleFailures {
    /**
     * Deal with Exceptions (both inside the Try and non-fatal thrown) in a NetLogo-friendly way.
     */
    def apply[R](body: => Try[R]): R = {
      val result = Try(body)
      result.flatten match {
        case Failure(he: HaltException) => throw he // We can't actually catch throw InterruptedExceptions, but in case there's a wrapped one
        case Failure(_: InterruptedException) =>
          Thread.interrupted()
          invalidateJobs()
          throw new HaltException(true)
        case Failure(ee: ExtensionException) => throw ee
        case Failure(ex: Exception) => throw new ExtensionException(ex)
        case Failure(th: Throwable) => throw th
        case Success(x) => x
      }
    }
  }

  private def async[R](body: => Try[R]): SyncVar[Try[R]] = {
    val result = new SyncVar[Try[R]]
    executor.execute { () =>
      try {
        isRunningLegitJob.set(true)
        result.put(body)
      } catch {
        case _: IOException if shuttingDown.get =>
        case e: IOException =>
          close()
          result.put(Failure(
            new ExtensionException(s"Disconnected from ${extensionLongName} unexpectedly. Try running $extensionName:setup again.", e)
          ))
        case _: InterruptedException => Thread.interrupted()
        case e: Exception => result.put(Failure(e))
      } finally {
        isRunningLegitJob.set(false)
      }
    }
    result
  }

  private def receive(send: => Unit): Try[AnyRef] = {
    Subprocess.logger.logOne("Subprocess.receive()")

    send

    // Subprocess.logger.logMany("Subprocess.receive()") { Seq("inReader.ready", inReader.ready.toString) }
    val line = inReader.readLine()
    if (line == null) {
      return Failure(new ExtensionException("Unable to read child process output. Try running the command again"))
    }
    val parsed = parse(line)

    val msg_type = (parsed \ "type") match {
      case JInt(num) => num.toInt

      case _ =>
        return Failure(new ExtensionException(s"Unknown message type received from the external langauge: ${parsed \ "type"}"))
    }

    val result = msg_type match {
      case Subprocess.InTypes.hearbeatResponseMsg =>
        Success("beat")

      case Subprocess.InTypes.successMsg =>
        val body = convert.toNetLogo(parsed \ "body")
        Success(body)

      case Subprocess.InTypes.errorMsg =>
        val message = (parsed \ "body" \ "message").asInstanceOf[JString].s
        val longMessage = (parsed \ "body" \ "longMessage").asInstanceOf[JString].s
        Failure(new ExtensionException(message, new TargetLanguageErrorException(message, longMessage)))

      case _ =>
        Failure(new ExtensionException(s"Unknown message type received from the external langauge: $msg_type"))
    }

    redirectPipes()
    result
  }

  private def heartbeat(timeout: Duration = 1.seconds): Try[Any] = {
    if (!isRunningLegitJob.get) {
      val hb = async {
        receive(sendHeartbeat())
      }
      hb.get(timeout.toMillis).getOrElse(
        Failure(new ExtensionException(
          s"${extensionLongName} is not responding. You can wait to see if it finishes what it's doing or restart it using ${extensionName}:setup"
        ))
      )
    } else {
      Success("beat")
    }
  }

  // --------------------Actual Message Formulation----------------------------
  private def sendMessage(msg: JObject): Unit = {
    Subprocess.logger.logMany("Subprocess.sendMessage()") { Seq("msg", msg.toString) }
    out.write(compact(render(msg)).getBytes("UTF-8"))
    Subprocess.logger.logMany("Subprocess.sendMessage()") { Seq("message written") }
    out.write('\r')
    out.write('\n')
    Subprocess.logger.logMany("Subprocess.sendMessage()") { Seq("line breaks written") }
    out.flush()
    Subprocess.logger.logMany("Subprocess.sendMessage()") { Seq("flushed") }
  }

  private def sendStmt(stmt: String): Unit = {
    val msg = ("type" -> Subprocess.OutTypes.stmtMsg) ~ ("body" -> stmt)
    sendMessage(msg)
  }

  private def sendExpr(expr: String): Unit = {
    val msg = ("type" -> Subprocess.OutTypes.exprMsg) ~ ("body" -> expr)
    sendMessage(msg)
  }

  private def sendExprStringified(expr: String): Unit = {
    val msg = ("type" -> Subprocess.OutTypes.exprStringifiedMsg) ~ ("body" -> expr)
    sendMessage(msg)
  }

  private def sendAssn(varName: String, value: AnyRef): Unit = {
    val name_value_pair = ("varName" -> varName) ~ ("value" -> convert.toJson(value))
    val msg = ("type" -> Subprocess.OutTypes.assnMsg) ~ ("body" -> name_value_pair)
    sendMessage(msg)
  }

  private def sendQuit(): Unit = {
    val msg = ("type" -> Subprocess.OutTypes.quitMsg)
    sendMessage(msg)
  }

  private def sendGeneric(i: Int, value: AnyRef): Unit = {
    val msg = ("type" -> i) ~ ("body" -> convert.toJson(value))
    sendMessage(msg)
  }

  private def sendHeartbeat(): Unit = {
    val msg = ("type" -> Subprocess.OutTypes.heartbeatRequestMsg)
    sendMessage(msg)
  }

}
