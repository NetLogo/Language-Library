package org.nlogo.languagelibrary.config

import java.awt.{ FileDialog, GridBagConstraints => GBC }
import java.io.File
import javax.swing.{ JDialog, JLabel, JPanel, JTextField }

import org.nlogo.swing.RichJButton

trait ConfigProperty {
  def key: String
  def value: String
  def addToPanel(parent: JDialog, panel: JPanel): Unit
}

class FileProperty(val key: String, val fileName: String, initialValue: String, message: String) extends ConfigProperty {
  private val pathTextField = new JTextField(initialValue, 20)

  def value = {
    pathTextField.getText
  }

  def addToPanel(parent: JDialog, panel: JPanel): Unit = {
    panel.add(new JLabel(message), Constraints(gridx = 0, gridw = 3))

    val pathLabel = s"$fileName Path"
    panel.add(new JLabel(pathLabel), Constraints(gridx = 0))
    panel.add(pathTextField, Constraints(gridx = 1, weightx = 1.0, fill = GBC.HORIZONTAL))
    panel.add(RichJButton("Browse...") {
      val userSelected = askForPath(parent, pathLabel, pathTextField.getText)
      userSelected.foreach(pathTextField.setText)
    }, Constraints(gridx = 2))
  }

  private def askForPath(parent: JDialog, name: String, current: String): Option[String] = {
    val dialog = new FileDialog(parent, s"Configure $name", FileDialog.LOAD)
    dialog.setDirectory(new File(current).getParent)
    dialog.setFile(new File(current).getName)
    dialog.setVisible(true)
    Option(dialog.getDirectory)
  }

}
