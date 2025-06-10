package org.nlogo.languagelibrary.config

import java.awt.{ BorderLayout, GridBagLayout }
import java.awt.event.ActionEvent
import javax.swing.{ AbstractAction, BorderFactory, JDialog, JFrame, JPanel }

import org.nlogo.core.I18N
import org.nlogo.swing.{ Button, ButtonPanel, Transparent, Utils }
import org.nlogo.theme.{ InterfaceColors, ThemeSync }

class ConfigEditor(owner: JFrame, longName: String, extLangBin: String, config: Config,
                   extraProperties: Seq[ConfigProperty]) extends JDialog(owner, longName) with ThemeSync {

  private val runtimeMessage = s"Enter the path to your $extLangBin executable. If blank, then $longName will look for an appropriate version of $extLangBin to run on the system's PATH."
  private val properties = Seq(new FileProperty("runtimePath", extLangBin, config.runtimePath.getOrElse(""), runtimeMessage)) ++ extraProperties

  private val okButton = new Button(I18N.gui.get("common.buttons.ok"), () => {
    save()
    dispose()
  })

  private val cancelButton = new Button(I18N.gui.get("common.buttons.cancel"), dispose)

  locally {
    getContentPane.setLayout(new BorderLayout)

    val mainPanel = new JPanel with Transparent

    mainPanel.setLayout(new BorderLayout)
    mainPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5))

    getContentPane.add(mainPanel, BorderLayout.CENTER)

    val editPanel = new JPanel with Transparent

    editPanel.setLayout(new GridBagLayout)

    properties.foreach( (prop) => {
      prop.addToPanel(this, editPanel)
    })

    val buttonPanel = new ButtonPanel(Seq(
      okButton,
      cancelButton
    ))

    getRootPane.setDefaultButton(okButton)

    mainPanel.add(editPanel, BorderLayout.CENTER)
    mainPanel.add(buttonPanel, BorderLayout.SOUTH)

    pack()

    Utils.addEscKeyAction(this, new AbstractAction {
      override def actionPerformed(e: ActionEvent): Unit = {
        dispose()
      }
    })
  }

  def save(): Unit = {
    properties.foreach( (prop) => {
      config.set(prop.key, prop.value)
    })
    config.save()
  }

  override def syncTheme(): Unit = {
    getContentPane.setBackground(InterfaceColors.dialogBackground())

    okButton.syncTheme()
    cancelButton.syncTheme()

    properties.foreach(_.syncTheme())
  }
}
