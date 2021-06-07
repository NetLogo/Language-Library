package org.me

import org.json4s.JsonAST.{JArray, JBool, JDecimal, JDouble, JInt, JLong, JNothing, JNull, JObject, JSet, JString, JValue}
import org.json4s.jackson.JsonMethods.{compact, mapper, parse, render}
import org.json4s.JsonDSL._
import org.nlogo.api.Exceptions.ignoring

import java.net.Socket
import org.nlogo.api.{ExtensionException, OutputDestination, Workspace}
import org.nlogo.app.App
import org.nlogo.core.{Dump, LogoList, Nobody}
import org.nlogo.nvm.HaltException
import org.nlogo.workspace.AbstractWorkspace

import java.lang.{Boolean => JavaBoolean, Double => JavaDouble}
import java.awt.GraphicsEnvironment
import java.io.{BufferedInputStream, BufferedOutputStream, BufferedReader, File, IOException, InputStreamReader}
import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.{JMenu, SwingUtilities}
import scala.concurrent.SyncVar
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._



object Subprocess {
  // In and out
  val typeSize = 1

  // Out types
  val stmtMsg = 0
  val exprMsg = 1
  val assnMsg = 2
  val exprStringifiedMsg = 3

  // In types
  val successMsg = 0
  val errorMsg = 1



  def start[A](ws: Workspace, processStartCmd: Seq[String], processStartArgs: Seq[String], extensionName : String, extensionLongName : String, suppliedPort : Int = 0): Subprocess = {
    val prefix = new File(ws.asInstanceOf[AbstractWorkspace].fileManager.prefix)
    val workingDirectory = if (prefix.exists) prefix else new File(System.getProperty("user.home"))
    val pb = new ProcessBuilder(cmd(processStartCmd ++ processStartArgs).asJava).directory(workingDirectory)
    val proc = pb.start()
    val pbInput = new BufferedReader(new InputStreamReader(proc.getInputStream))

    val port = if (suppliedPort != 0) {
      suppliedPort
    } else {
      getPortFromProc(proc, pbInput)
    }

    val socket = getSocketConnection(proc, port)

    if (!proc.isAlive) { earlyFail(proc, s"Process terminated early.")}
    val subprocess = new Subprocess(ws, proc, socket, extensionName, extensionLongName)

    // This is being created in the totally wrong place right now
    val shellWindow = new ShellWindow(subprocess.evalStringified);
    shellWindow.setVisible(true);

    subprocess
  }

  private def getPortFromProc[A](proc: Process, pbInput: BufferedReader): Int = {
    val portLine = pbInput.readLine
    try {
      portLine.toInt
    } catch {
      case _: NumberFormatException =>
        earlyFail(proc, s"Process did not provide expected output. Expected a port number but got:\n$portLine")
    }
  }

  private def getSocketConnection(proc: Process, port : Int) : Socket = {
    var socket: Socket = null
    while (socket == null && proc.isAlive) {
      try {
        socket = new Socket("localhost", port)
      } catch {
        case _:IOException => // Keep trying
        case e: SecurityException => throw new ExtensionException(e)
      }
    }
    socket
  }

  def earlyFail(proc: Process, prefix: String) : Nothing = {
    val stdout = readAllReady(new InputStreamReader(proc.getInputStream))
    val stderr = readAllReady(new InputStreamReader(proc.getErrorStream))
    val msg = (stderr, stdout) match {
      case ("", s) => s
      case (s, "") => s
      case (e, o) => s"Error output:\n$e\n\nOutput:\n$o"
    }
    throw new ExtensionException(s"prefix\n$msg")
  }

  private def readAllReady(in: InputStreamReader): String = {
    val sb = new StringBuilder
    while (in.ready) sb.append(in.read().toChar)
    sb.toString
  }

  private def cmd(args: Seq[String]): Seq[String] = {
    val os = System.getProperty("os.name").toLowerCase
    if (os.contains("mac"))
      List("/bin/bash", "-l", "-c", args.map(a => s"'$a'").mkString(" "))
    else
      args
  }

  def path: Seq[File] = {
    val basePath = System.getenv("PATH")
    val os = System.getProperty("os.name").toLowerCase

    val unsplitPath = if (os.contains("mac") && basePath == "/usr/bin:/bin:/usr/sbin:/sbin")
    // On MacOS, .app files are executed with a neutered PATH environment variable. The problem is that if users are
    // using Homebrew Python or similar, it won't be on that PATH. So, we check if we're on MacOS and if we have that
    // neuteredPATH. If so, we want to execute with the users actual PATH. We use `path_helper` to get that. It's not
    // perfect; it will miss PATHs defined in certain files, but hopefully it's good enough.
      getCmdOutput("/bin/bash", "-l", "-c", "echo $PATH").head + basePath
    else
      basePath
    unsplitPath.split(File.pathSeparatorChar).map(new File(_)).filter(f => f.isDirectory)
  }

  private def getCmdOutput(cmd: String*): List[String] = {
    val proc = new ProcessBuilder(cmd: _*).redirectError(Redirect.PIPE).redirectInput(Redirect.PIPE).start()
    val in = new BufferedReader(new InputStreamReader(proc.getInputStream))
    Iterator.continually(in.readLine()).takeWhile(_ != null).toList
  }

}

object LanguageServerMenu {

}

class LanguageServerMenu(name : String) extends JMenu(name) {
  add("Item")
  add("Another Item")
}

class Subprocess(ws: Workspace, proc: Process, socket: Socket, extensionName : String, extensionLongName : String) {

  // TODO: make this init on extension loading, not on subprocess starting. will take some refactoring
  var languageServerMenu: Option[JMenu] = None
  if (!ws.isHeadless) {
    val menuBar = App.app.frame.getJMenuBar

    menuBar.getComponents.collectFirst {
      case mi: JMenu if mi.getText ==  extensionName => mi
    }.getOrElse {
      languageServerMenu = Option(menuBar.add(new LanguageServerMenu(extensionName)))
    }
  }

  private val shuttingDown = new AtomicBoolean(false)
  private val isRunningLegitJob = new AtomicBoolean(false)

  val inReader = new BufferedReader(new InputStreamReader(socket.getInputStream))

  val out = new BufferedOutputStream(socket.getOutputStream)

  val stdout = new InputStreamReader(proc.getInputStream)
  val stderr = new InputStreamReader(proc.getErrorStream)

  private val executor : ExecutorService = Executors.newSingleThreadExecutor()

  object Haltable {
    def apply[R](body: => R): R = try body catch {
      case _: InterruptedException =>
        Thread.interrupted()
        invalidateJobs()
        throw new HaltException(true)
      case e: Throwable => throw e
    }
  }

  object HandleFailures {
    /**
     * Deal with Exceptions (both inside the Try and non-fatal thrown) in a NetLogo-friendly way.
     */
    def apply[R](body: => Try[R]): R = Try(body).flatten match {
      case Failure(he: HaltException) => throw he// We can't actually catch throw InterruptedExceptions, but in case there's a wrapped one
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

  def ensureValidNum(d: Double): Double = d match {
    case x if x.isInfinite => throw new ExtensionException("External language reported a number too large for NetLogo.")
    case x if x.isNaN => throw new ExtensionException("External language reported a non-numeric value from a mathematical operation.")
    case x => x
  }

  def output(s: String): Unit = {
    if (GraphicsEnvironment.isHeadless || System.getProperty("org.nlogo.preferHeadless") == "true")
      println(s)
    else
      SwingUtilities.invokeLater { () =>
        ws.outputObject(s, null, addNewline = true, readable = false, OutputDestination.Normal)
      }
  }

  def redirectPipes(): Unit = {
    val stdoutContents = Subprocess.readAllReady(stdout)
    val stderrContents = Subprocess.readAllReady(stderr)
    if (stdoutContents.nonEmpty)
      output(stdoutContents)
    if (stderrContents.nonEmpty)
      output(s"${extensionLongName} Error output:\n$stderrContents")
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
    send

    val line = inReader.readLine()
    val parsed = parse(line)

    val msg_type = toLogo(parsed \ "type")

    val result = if (msg_type == 0) {
      val body = toLogo(parsed \ "body")
      Success(body)
    } else {
      val message = compact(render((parsed \ "body" \ "message"))).drop(1).dropRight(1)
      val cause = compact(render(parsed \ "body" \ "cause")).drop(1).dropRight(1)
      // Without these drop's, the strings would have quotes inside of them
      Failure(new ExtensionException(message, new Exception(cause)))
    }

    redirectPipes()
    result
  }

  def heartbeat(timeout: Duration = 1.seconds): Try[Any] = if (!isRunningLegitJob.get) {
    val hb = async {
      receive {sendStmt("")}
    }
    hb.get(timeout.toMillis).getOrElse(
      Failure(new ExtensionException(
        s"${extensionLongName} is not responding. You can wait to see if it finishes what it's doing or restart it using ${extensionName}:setup"
      ))
    )
  } else Success(())

  def exec(stmt: String): AnyRef = {
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendStmt(stmt))
        })
      }
    }.get.get
  }

  def eval(expr: String): AnyRef = {
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendExpr(expr))
        })
      }
    }.get.get
  }

  def evalStringified(expr: String): AnyRef = {
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendExprStringified(expr))
        })
      }
    }.get.get
  }

  def assign(varName: String, value: AnyRef): AnyRef = {
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendAssn(varName, value))
        })
      }
    }.get.get
  }

  def genericJson(msg_type: Int, value: JValue): AnyRef = {
    generic(msg_type, value)
  }

  def generic(msg_type: Int, value: AnyRef): AnyRef = {
    HandleFailures {
      Haltable {
        heartbeat().map(_ => async {
          receive(sendGeneric(msg_type, value))
        })
      }
    }.get.get
  }

  private def sendMessage(msg: JObject): Unit = {
    out.write(compact(render(msg)).getBytes("UTF-8"))
    out.write('\n')
    out.flush()
  }

  private def sendStmt(stmt: String): Unit = {
    val msg = ("type" -> Subprocess.stmtMsg) ~ ("body" -> stmt)
    sendMessage(msg)
  }

  private def sendExpr(expr: String): Unit = {
    val msg = ("type" -> Subprocess.exprMsg) ~ ("body" -> expr)
    sendMessage(msg)
  }

  private def sendExprStringified(expr: String): Unit = {
    val msg = ("type" -> Subprocess.exprStringifiedMsg) ~ ("body" -> expr)
    sendMessage(msg)
  }

  private def sendAssn(varName: String, value: AnyRef): Unit = {
    val name_value_pair = ("varName" -> varName) ~ ("value" -> toJson(value))
    val msg = ("type" -> Subprocess.assnMsg) ~ ("body" -> name_value_pair)
    sendMessage(msg)
  }

  private def sendGeneric(i: Int, value: AnyRef): Unit = {
    val msg = ("type" -> i) ~ ("body" -> toJson(value))
    sendMessage(msg)
  }

  def toJson(x: AnyRef): JValue = x match {
    case j: JValue => j
    case s: String => JString(s)
    case l: LogoList => l.map(toJson)
    case b: java.lang.Boolean => if (b) JBool.True else JBool.False
    case Nobody => JNothing
    case o => parse(Dump.logoObject(o, readable = true, exporting = false))
  }

  def toLogo(s: String): AnyRef = toLogo(parse(s))
  def toLogo(x: JValue): AnyRef = x match {
    case JNothing => Nobody
    case JNull => Nobody
    case JString(s) => s
    case JDouble(num) => ensureValidNum(num): JavaDouble
    case JDecimal(num) => ensureValidNum(num.toDouble): JavaDouble
    case JLong(num) => ensureValidNum(num.toDouble): JavaDouble
    case JInt(num) => ensureValidNum(num.toDouble): JavaDouble
    case JBool(value) => value: JavaBoolean
    case JObject(obj) => LogoList.fromVector(obj.map(f => LogoList(f._1, toLogo(f._2))).toVector)
    case JArray(arr) => LogoList.fromVector(arr.map(toLogo).toVector)
    case JSet(set) => LogoList.fromVector(set.map(toLogo).toVector)
  }

  def close(): Unit = {
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

  def invalidateJobs(): Unit = isRunningLegitJob.set(false)
}