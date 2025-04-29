package org.nlogo.languagelibrary.config

import java.awt.{ BorderLayout, GridBagLayout }
import java.awt.event.ActionEvent
import javax.swing.{ AbstractAction, BorderFactory, JButton, JDialog, JFrame, JPanel }

import org.nlogo.core.I18N
import org.nlogo.swing.{ ButtonPanel, RichAction, Utils }

class ConfigEditor(owner: JFrame, longName: String, extLangBin: String, config: Config, extraProperties: Seq[ConfigProperty]) extends JDialog(owner, longName) {
  private val runtimeMessage = s"Enter the path to your $extLangBin executable. If blank, then $longName will look for an appropriate version of $extLangBin to run on the system's PATH."
  private val properties = Seq(new FileProperty("runtimePath", extLangBin, config.runtimePath.getOrElse(""), runtimeMessage)) ++ extraProperties

  {
    getContentPane.setLayout(new BorderLayout)
    val mainPanel = new JPanel
    mainPanel.setLayout(new BorderLayout)
    mainPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5))
    getContentPane.add(mainPanel, BorderLayout.CENTER)

    val editPanel = new JPanel
    editPanel.setLayout(new GridBagLayout)

    properties.foreach( (prop) => {
      prop.addToPanel(this, editPanel)
    })

    val okButton = new JButton(new AbstractAction(I18N.gui.get("common.buttons.ok")) {
      override def actionPerformed(e: ActionEvent): Unit = {
        save()
        dispose()
      }
    })
    val cancelAction = RichAction(I18N.gui.get("common.buttons.cancel"))(_ => dispose())
    val buttonPanel = new ButtonPanel(Seq(
      okButton,
      new JButton(cancelAction)
    ))
    getRootPane.setDefaultButton(okButton)
    Utils.addEscKeyAction(this, cancelAction)

    mainPanel.add(editPanel, BorderLayout.CENTER)
    mainPanel.add(buttonPanel, BorderLayout.SOUTH)
    pack()
  }

  def save(): Unit = {
    properties.foreach( (prop) => {
      config.set(prop.key, prop.value)
    })
    config.save()
  }
}
