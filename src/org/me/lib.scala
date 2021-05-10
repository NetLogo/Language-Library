package org.me

import org.json4s.JsonAST.{JArray, JBool, JDecimal, JDouble, JInt, JLong, JNothing, JNull, JObject, JSet, JString, JValue}
import org.json4s.jackson.JsonMethods.{mapper, parse}
import org.nlogo.api.Exceptions.ignoring

import java.net.Socket
import org.nlogo.api.{ExtensionException, OutputDestination, Workspace}
import org.nlogo.core.{Dump, LogoList, Nobody}
import org.nlogo.workspace.AbstractWorkspace

import java.lang.{Boolean => JavaBoolean, Double => JavaDouble}
import java.awt.GraphicsEnvironment
import java.io.{BufferedInputStream, BufferedOutputStream, BufferedReader, File, IOException, InputStreamReader}
import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
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

  // In types
  val successMsg = 0
  val errorMsg = 1

  def start[A](ws: Workspace, processStartCmd: Seq[String], processStartArgs: Seq[String], extensionName : String, extensionLongName : String): Subprocess = {

    def earlyFail(proc: Process, prefix: String) = {
      val stdout = readAllReady(new InputStreamReader(proc.getInputStream))
      val stderr = readAllReady(new InputStreamReader(proc.getErrorStream))
      val msg = (stderr, stdout) match {
        case ("", s) => s
        case (s, "") => s
        case (e, o) => s"Error output:\n$e\n\nOutput:\n$o"
      }
      throw new ExtensionException(s"prefix\n$msg")
    }

    ws.getExtensionManager

    val prefix = new File(ws.asInstanceOf[AbstractWorkspace].fileManager.prefix)

    val workingDirectory = if (prefix.exists) prefix else new File(System.getProperty("user.home"))
    val pb = new ProcessBuilder(cmd(processStartCmd ++ processStartArgs).asJava).directory(workingDirectory)
    val proc = pb.start()

    val pbInput = new BufferedReader(new InputStreamReader(proc.getInputStream))
    val portLine = pbInput.readLine

    val port = try {
      portLine.toInt
    } catch {
      case _: java.lang.NumberFormatException =>
        earlyFail(proc, s"Process did not provide expected output. Expected a port number but got:\n$portLine")
    }

    var socket: Socket = null
    while (socket == null && proc.isAlive) {
      try {
        socket = new Socket("localhost", port)
      } catch {
        case _:IOException => // Keep trying
        case e: SecurityException => throw new ExtensionException(e)
      }
    }
    if (!proc.isAlive) { earlyFail(proc, s"Process terminated early.")}
    new Subprocess(ws, proc, socket, extensionName, extensionLongName)
  }

  def readAllReady(in: InputStreamReader): String = {
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
      getCmdOutput("/bin/bash", "-l", "-c", "echo $PATH").head ++ basePath
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

class Subprocess(ws: Workspace, proc: Process, socket: Socket, extensionName : String, extensionLongName : String) {

  private val shuttingDown = new AtomicBoolean(false)
  private val isRunningLegitJob = new AtomicBoolean(false)

  val in = new BufferedInputStream(socket.getInputStream)
  val out = new BufferedOutputStream(socket.getOutputStream)

  val stdout = new InputStreamReader(proc.getInputStream)
  val stderr = new InputStreamReader(proc.getErrorStream)

  private val executor : ExecutorService = Executors.newSingleThreadExecutor()

  def ensureValidNum(d: Double): Double = d match {
    case x if x.isInfinite => throw new ExtensionException("Python reported a number too large for NetLogo.")
    case x if x.isNaN => throw new ExtensionException("Python reported a non-numeric value from a mathematical operation.")
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

  private def run[R](send: => Unit)(read: => R): Try[R] = {
    send
    val t = readByte()
    val result = if (t == 0) {
      Success(read)
    } else {
      Failure(clientException())
    }
    redirectPipes()
    result
  }

  def heartbeat(timeout: Duration = 1.seconds): Try[Unit] = if (!isRunningLegitJob.get) {
    val hb = async {
      run {sendStmt("")} {()}
    }
    hb.get(timeout.toMillis).getOrElse(
      Failure(new ExtensionException(
        s"${extensionLongName} is not responding. You can wait to see if it finishes what it's doing or restart it using ${extensionName}:setup"
      ))
    )
  } else Success(())

  def exec(stmt: String): Try[SyncVar[Try[Unit]]] =
    heartbeat().map(_ => async {
      run(sendStmt(stmt))(())
    })

  def eval(expr: String): Try[SyncVar[Try[AnyRef]]] =
    heartbeat().map(_ => async {
      run(sendExpr(expr))(readLogo())
    })

  def assign(varName: String, value: AnyRef): Try[SyncVar[Try[Unit]]] =
    heartbeat().map(_ => async {
      run(sendAssn(varName, value))(())
    })

  def clientException(): Exception = {
    val e = readString()
    val tb = readString()
    new ExtensionException(e, new Exception(tb))
  }

  private def sendStmt(msg: String): Unit = {
    out.write(Subprocess.stmtMsg)
    writeString(msg)
    out.flush()
  }

  private def sendExpr(msg: String): Unit = {
    out.write(Subprocess.exprMsg)
    writeString(msg)
    out.flush()
  }

  private def sendAssn(varName: String, value: AnyRef): Unit = {
    out.write(Subprocess.assnMsg)
    writeString(varName)
    writeString(toJson(value))
    out.flush()
  }

  private def read(numBytes: Int): Array[Byte] = Array.fill(numBytes)(readByte())

  private def readByte(): Byte = {
    val nextByte = in.read()
    if (nextByte == -1) {
      throw new IOException("Reached end of stream")
    }
    nextByte.toByte
  }

  private def readInt(): Int = {
    (readByte() << 24) & 0xff000000 |
    (readByte() << 16) & 0x00ff0000 |
    (readByte() <<  8) & 0x0000ff00 |
    (readByte() <<  0) & 0x000000ff
  }

  private def readString(): String = {
    val l = readInt()
    val s = new String(read(l), "UTF-8")
    s
  }

  private def readLogo(): AnyRef = toLogo(readString())

  private def writeInt(i: Int): Unit = {
    val a = Array((i >>> 24).toByte, (i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
    out.write(a)
  }

  private def writeString(str: String): Unit = {
    val bytes = str.getBytes("UTF-8")
    writeInt(bytes.length)
    out.write(bytes)
  }

  def toJson(x: AnyRef): String = x match {
    case l: LogoList => "[" + l.map(toJson).mkString(", ") + "]"
    case b: java.lang.Boolean => if (b) "true" else "false"
    case Nobody => "None"
    case o => Dump.logoObject(o, readable = true, exporting = false)
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
    ignoring(classOf[IOException])(in.close())
    ignoring(classOf[IOException])(out.close())
    ignoring(classOf[IOException])(socket.close())
    proc.destroyForcibly()
    proc.waitFor(3, TimeUnit.SECONDS)
    if (proc.isAlive)
      throw new ExtensionException(s"${extensionLongName} process failed to shutdown. Please shut it down via your process manager")
  }

  def invalidateJobs(): Unit = isRunningLegitJob.set(false)
}