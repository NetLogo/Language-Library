package org.nlogo.languagelibrary.config

import java.awt.GraphicsEnvironment

import org.nlogo.api.ExtensionManager
import org.nlogo.workspace.{ AbstractWorkspace, ExtensionManager => WorkspaceExtensionManager }

object Platform {
  val isHeadless =
    GraphicsEnvironment.isHeadless ||
    "true".equals(System.getProperty("java.awt.headless")) ||
    "true".equals(System.getProperty("org.nlogo.preferHeadless"))

  def isHeadless(em: ExtensionManager): Boolean = {
    // "Can't we just check the `org.nlogo.preferHeadless` property?"  Well, kind-of, but
    // it turns out that doesn't get set automatically and there are a lot of ways to run
    // NetLogo models headlessly that "forget" to do it.  It's safer to check if the
    // workspace we're using is headless in addition to checking the property.  -Jeremy B
    // July 2022
    Platform.isHeadless ||
      (em.isInstanceOf[WorkspaceExtensionManager] &&
        em.asInstanceOf[WorkspaceExtensionManager].workspace.isInstanceOf[AbstractWorkspace] &&
        em.asInstanceOf[WorkspaceExtensionManager].workspace.asInstanceOf[AbstractWorkspace].isHeadless)
  }

  val isWindows =
    System.getProperty("os.name").toLowerCase.startsWith("win")

}
