package org.nlogo.languagelibrary.config

import java.io.{ File, FileInputStream, FileOutputStream }
import java.lang.Class
import java.nio.file.Paths
import java.util.Properties

import org.nlogo.api.{ FileIO, ExtensionException }

object Config {

  def getExtensionRuntimeDirectory(extensionClass: Class[?], codeName: String) = {
    val configLoader       = extensionClass.getClassLoader.asInstanceOf[java.net.URLClassLoader]
    val loaderUrls         = configLoader.getURLs()
    val loaderFiles        = loaderUrls.map( (url) => new File(url.toURI.getPath) )
    val jarName            = s"$codeName.jar"
    val maybeExtensionFile = loaderFiles.find( (f) => jarName.equals(f.getName()) )
    val extensionFile      = maybeExtensionFile.getOrElse(
      throw new ExtensionException(s"Could not locate the extension $jarName file to determine the runtime directory?")
    )
    extensionFile.getParentFile
  }

  def createForPropertyFile(extensionClass: Class[?], codeName: String): Config = {
    val propertyFileName          = s"$codeName.properties"
    val extensionRuntimeDirectory = getExtensionRuntimeDirectory(extensionClass, codeName)
    val maybePropertyFile         = new File(extensionRuntimeDirectory, propertyFileName)
    val propertyFile              = if (maybePropertyFile.exists) {
      maybePropertyFile
    } else {
      new File(FileIO.perUserDir(codeName), propertyFileName)
    }
    Config(propertyFile)
  }

  private def checkRuntimePath(checkPath: String, checkFlags: Seq[String]): Boolean = {
    import scala.sys.process._
    val procSetup = Seq(checkPath) ++ checkFlags
    try {
      procSetup.! == 0
    } catch {
      case _: Throwable => false
    }
  }

  def getRuntimePath(extLangBin: String, maybeConfigPath: String, checkFlags: String*): Option[String] = {
    val checkPath = maybeConfigPath.trim()
    def fallback() =
      if (checkRuntimePath(extLangBin, checkFlags)) {
        Some(extLangBin)
      } else {
        None
      }

    // if checkPath is empty, just use the extLangBin...
    if (checkPath != "" && checkPath != extLangBin) {
      // if it's not empty, test if it's a dir or not
      val checkFile = new File(checkPath)
      if (checkFile.isDirectory) {
        // directory, append the bin
        val configRuntimePath = Paths.get(checkPath, extLangBin).toString
        if (checkRuntimePath(configRuntimePath, checkFlags)) {
          return Some(configRuntimePath)
        }
        System.err.println(s"The path for $extLangBin is configured ($checkPath), but it is a directory and no runnable file is found there.  Falling back to the system path.")

      } else {
        // not a directory, just try it out
        if (checkRuntimePath(checkPath, checkFlags)) {
          return Some(checkPath)
        }
        System.err.println(s"The path for $extLangBin is configured ($checkPath), but it is a file that is not runnable.  Falling back to the system path.")
      }
    }

    fallback()
  }

}

case class Config(propertyFile: File) {

  protected val properties = new Properties

  if (propertyFile.exists) {
    Using(new FileInputStream(propertyFile)) { f => properties.load(f) }
  }

  def save(): Unit = {
    Using(new FileOutputStream(propertyFile)) { f => properties.store(f, "") }
  }

  def runtimePath: Option[String] = {
    Option(properties.getProperty("runtimePath")).flatMap( path => if (path.trim.isEmpty) None else Some(path) )
  }

  def runtimePath_=(path: String): Unit = {
    properties.setProperty("runtimePath", path)
  }

  def get(key: String): Option[String] = {
    Option(properties.getProperty(key))
  }

  def set(key: String, value: String): Unit = {
    properties.setProperty(key, value)
  }

}
