package org.nlogo.languagelibrary.config

import java.awt.{ BorderLayout, GridBagLayout }
import javax.swing.{ BorderFactory, JButton, JDialog, JFrame, JPanel }

import org.nlogo.core.I18N
import org.nlogo.swing.{ ButtonPanel, RichAction, RichJButton, Utils }

class ConfigEditor(owner: JFrame, longName: String, extLangBin: String, config: Config, extraProperties: Seq[ConfigProperty]) extends JDialog(owner, longName) {
  private val runtimeMessage = s"Enter the path to your $extLangBin executable. If blank, the $longName will attempt to find an appropriate version of $extLangBin to run on the system's PATH."
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

    val okButton = RichJButton(I18N.gui.get("common.buttons.ok")) {
      save()
      dispose()
    }
    val cancelAction = RichAction(I18N.gui.get("common.buttons.cancel"))(_ => dispose())
    val buttonPanel = ButtonPanel(
      okButton,
      new JButton(cancelAction)
    )
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
    config.save
  }
}
