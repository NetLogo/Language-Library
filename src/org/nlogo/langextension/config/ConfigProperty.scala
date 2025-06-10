package org.nlogo.languagelibrary.config

import java.awt.{ FileDialog, GridBagConstraints => GBC }
import java.io.File
import java.nio.file.Paths
import javax.swing.{ JDialog, JLabel, JPanel }

import org.nlogo.swing.{ Button, TextField }
import org.nlogo.theme.{ InterfaceColors, ThemeSync }

trait ConfigProperty extends ThemeSync {
  def key: String
  def value: String
  def addToPanel(parent: JDialog, panel: JPanel): Unit
}

class FileProperty(val key: String, val fileName: String, initialValue: String, message: String) extends ConfigProperty {
  private var parent: JDialog = null

  private val messageLabel = new JLabel(message)
  private val pathLabel = new JLabel(s"$fileName Path")
  private val pathTextField = new TextField(20, initialValue)
  private val browseButton = new Button("Browse...", () => {
    val userSelected = askForPath(pathLabel.getText, pathTextField.getText)
    userSelected.foreach(pathTextField.setText)
  })

  def value: String = {
    pathTextField.getText
  }

  def addToPanel(parent: JDialog, panel: JPanel): Unit = {
    this.parent = parent

    panel.add(messageLabel, Constraints(gridx = 0, gridw = 3))
    panel.add(pathLabel, Constraints(gridx = 0))
    panel.add(pathTextField, Constraints(gridx = 1, weightx = 1.0, fill = GBC.HORIZONTAL))
    panel.add(browseButton, Constraints(gridx = 2))
  }

  private def askForPath(name: String, current: String): Option[String] = {
    val dialog = new FileDialog(parent, s"Configure $name", FileDialog.LOAD)
    dialog.setDirectory(new File(current).getParent)
    dialog.setFile(new File(current).getName)
    dialog.setVisible(true)
    val file = dialog.getFile
    if (file != null) {
      val path = Paths.get(dialog.getDirectory, file).toString
      Some(path)
    } else {
      None
    }
  }

  override def syncTheme(): Unit = {
    messageLabel.setForeground(InterfaceColors.dialogText())
    pathLabel.setForeground(InterfaceColors.dialogText())

    pathTextField.syncTheme()
    browseButton.syncTheme()
  }
}
