package org.nlogo.languagelibrary.config

import java.awt.event.ActionEvent
import javax.swing.AbstractAction

import org.nlogo.api.ExtensionManager
import org.nlogo.app.App
import org.nlogo.languagelibrary.ShellWindow
import org.nlogo.swing.{ Menu => NLMenu, MenuItem }

object Menu {

  def create(em: ExtensionManager, longName: String, extLangBin: String, config: Config, extraProperties: Seq[ConfigProperty] = Seq()): Option[Menu] = {
    // My gut tells me this information should be way easier for an extension to find, but
    // at the moment I don't feel like digging into the NetLogo side to figure out the
    // right place.  So we'll do this.  -Jeremy B July 2022
    if (!em.workspaceContext.workspaceGUI) {
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

class Menu(private val shellWindow: ShellWindow, longName: String, extLangBin: String, config: Config,
           extraProperties: Seq[ConfigProperty]) extends NLMenu(longName) {

  private val functionID = App.app.addSyncFunction(() => { syncTheme() })

  // lazy because org.nlogo.swing.Menu calls syncTheme() in its constructor (Isaac B 6/10/25)
  private lazy val configEditor = new ConfigEditor(App.app.frame, longName, extLangBin, config, extraProperties)

  def setup(evalStringified: (String) => String) = {
    shellWindow.setEvalStringified(Some(evalStringified))
  }

  def unload() = {
    configEditor.setVisible(false)
    shellWindow.setVisible(false)
    App.app.frame.getJMenuBar.remove(this)
    App.app.removeSyncFunction(functionID)
  }

  def showShellWindow() = {
    shellWindow.setVisible(true)
  }

  add(new MenuItem(new AbstractAction("Configure") {
    override def actionPerformed(e: ActionEvent): Unit = {
      configEditor.setVisible(true)
    }
  }))

  add(new MenuItem(new AbstractAction("Pop-out Interpreter") {
    override def actionPerformed(e: ActionEvent): Unit = {
      shellWindow.setVisible(!shellWindow.isVisible)
    }
  }))

  override def syncTheme(): Unit = {
    super.syncTheme()

    configEditor.syncTheme()
    shellWindow.syncTheme()
  }
}
