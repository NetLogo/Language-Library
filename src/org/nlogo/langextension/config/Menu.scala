package org.nlogo.languagelibrary.config

import javax.swing.JMenu

import org.nlogo.api.ExtensionManager
import org.nlogo.app.App
import org.nlogo.workspace.{ AbstractWorkspace, ExtensionManager => WorkspaceExtensionManager }
import org.nlogo.languagelibrary.ShellWindow

object Menu {

  def create(em: ExtensionManager, longName: String, extLangBin: String, config: Config, extraProperties: Seq[ConfigProperty] = Seq()): Option[Menu] = {
    // My gut tells me this information should be way easier for an extension to find, but
    // at the moment I don't feel like digging into the NetLogo side to figure out the
    // right place.  So we'll do this.  -Jeremy B July 2022
    val isHeadlessWorkspace = em.isInstanceOf[WorkspaceExtensionManager] &&
      em.asInstanceOf[WorkspaceExtensionManager].workspace.isInstanceOf[AbstractWorkspace] &&
      em.asInstanceOf[WorkspaceExtensionManager].workspace.asInstanceOf[AbstractWorkspace].isHeadless
    if (isHeadlessWorkspace || Platform.isHeadless) {
      None
    } else {
      val menuBar   = App.app.frame.getJMenuBar
      // I'm struggly to think how this would need to be called, as the extension should
      // always be unloaded and removed from the menu before being re-created for a new
      // model or whatever, but I guess it doesn't hurt to leave it in?
      // -Jeremy B April 2022
      val maybeMenu = menuBar.getComponents.collectFirst {
        case mi: Menu if mi.getText == longName => mi
      }
      Option(maybeMenu.getOrElse({
        val shellWindow = new ShellWindow()
        val menu        = new Menu(shellWindow, longName, extLangBin, config, extraProperties)
        menuBar.add(menu)
        menu
      }))
    }
  }

}

class Menu(private val shellWindow: ShellWindow, longName: String, extLangBin: String, config: Config, extraProperties: Seq[ConfigProperty]) extends JMenu(longName) {
  def setup(evalStringified: (String) => String) = {
    shellWindow.setEvalStringified(Some(evalStringified))
  }

  def unload() = {
    shellWindow.setVisible(false)
    App.app.frame.getJMenuBar.remove(this)
  }

  def showShellWindow() = {
    shellWindow.setVisible(true)
  }

  add("Configure").addActionListener { _ =>
    new ConfigEditor(App.app.frame, longName, extLangBin, config, extraProperties).setVisible(true)
  }

  add("Pop-out Interpreter").addActionListener { _ =>
    shellWindow.setVisible(!shellWindow.isVisible)
  }

}
