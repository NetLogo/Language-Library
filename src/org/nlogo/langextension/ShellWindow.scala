package org.nlogo.languagelibrary

import java.awt.{ BorderLayout, Dimension }
import java.awt.event.{ ActionEvent, ComponentAdapter, ComponentEvent, KeyAdapter, KeyEvent }
import javax.swing.{ AbstractAction, JFrame, JPanel, JSplitPane, ScrollPaneConstants }

import org.nlogo.api.ExtensionException
import org.nlogo.swing.{ Button, MenuItem, PopupMenu, ScrollPane, TextArea, Transparent }
import org.nlogo.theme.{ InterfaceColors, ThemeSync }

/**
 * A ShellWindow lets users interact with the target language through an
 * interactive shell/REPL. It handles most all of its own functionality.
 * The extension code only needs to manage its lifecycle and set an
 * `evalStringified` function to be called when evaluating input code.
 */
class ShellWindow extends JFrame with ThemeSync {
  // **functional state**
  private var evalStringified: Option[(String) => String] = None
  private var cmdHistory: Seq[String] = Seq()
  private var cmdHistoryIndex = -1

  // **Swing objects**
  private val consolePanel: JSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT)
  val output = new TextArea(0, 0, "")
  val input = new TextArea(0, 0, "")

  private val sp1 = new ScrollPane(output, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
  private val sp2 = new ScrollPane(input, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)

  val bottomContainer = new JPanel with Transparent
  val runButton = new Button("Run", runCode)
  val clearCodeAreaButton = new Button("Clear Code", () => input.setText(""))

  val topContainer = new JPanel with Transparent
  val clearHistoryAreaButton = new Button("Clear History", () => output.setText(""))

  private val outputContextMenu = new PopupMenu("Edit")
  private val inputContextMenu = new PopupMenu("Edit")

  outputContextMenu.add(new MenuItem(new AbstractAction("Copy Selected Text") {
    def actionPerformed(e: ActionEvent): Unit = {
      output.copy()
    }
  }))

  outputContextMenu.add(new MenuItem(new AbstractAction("Clear Text") {
    def actionPerformed(e: ActionEvent): Unit = {
      output.setText("")
    }
  }))

  inputContextMenu.add(new MenuItem(new AbstractAction("Copy Selected Text") {
    def actionPerformed(e: ActionEvent): Unit = {
      input.copy()
    }
  }))

  inputContextMenu.add(new MenuItem(new AbstractAction("Clear Text") {
    def actionPerformed(e: ActionEvent): Unit = {
      input.setText("")
    }
  }))

  output.setText(
    "Usage:\n\n"
      + "Write commands in the lower area and hit Ctrl-Enter to submit them.\n"
      + "Use page up/down to recall previously submitted commands.\n\n"
  )

  initPanels()

  // ---------------------Getters and Setters----------------------------------

  def setEvalStringified(_evalStringified: Option[(String) => String]): Unit = {
    evalStringified = _evalStringified
  }

  def getEvalStringified: Option[(String) => String] = evalStringified

  def getCmdHistory: Seq[String] = cmdHistory

  // -------------------------Helpers------------------------------------------

  private def initPanels(): Unit = {
    input.addKeyListener(new KeyAdapter {
      override def keyPressed(ke: KeyEvent): Unit = {
        if (ke.isControlDown && ke.getKeyCode == KeyEvent.VK_ENTER) {
          runCode()
        } else if (ke.getKeyCode == KeyEvent.VK_PAGE_UP && cmdHistoryIndex < cmdHistory.size - 1) {
          cmdHistoryIndex += 1

          input.setText(cmdHistory(cmdHistoryIndex))
          input.setCaretPosition(input.getText.size)
        } else if (ke.getKeyCode == KeyEvent.VK_PAGE_DOWN && cmdHistoryIndex > -1) {
          cmdHistoryIndex -= 1

          if (cmdHistoryIndex == -1) {
            input.setText("")
          } else {
            input.setText(cmdHistory(cmdHistoryIndex))
            input.setCaretPosition(input.getText.size)
          }
        }
      }
    })

    consolePanel.setTopComponent(sp1)
    consolePanel.setBottomComponent(sp2)

    topContainer.setLayout(new BorderLayout())
    topContainer.add(clearHistoryAreaButton, BorderLayout.EAST)

    bottomContainer.setLayout(new BorderLayout())
    bottomContainer.add(clearCodeAreaButton, BorderLayout.WEST)
    bottomContainer.add(runButton, BorderLayout.EAST)

    this.getContentPane.setLayout(new BorderLayout)
    this.getContentPane.add(consolePanel, BorderLayout.CENTER)
    this.getContentPane.add(bottomContainer, BorderLayout.AFTER_LAST_LINE)
    this.getContentPane.add(topContainer, BorderLayout.BEFORE_FIRST_LINE)

    this.setMinimumSize(new Dimension(400, 400))
    this.setSize(new Dimension(555, 650))
    output.setEditable(false)

    consolePanel.setDividerLocation((this.getHeight.asInstanceOf[Double] * 0.65).toInt)
    this.addComponentListener(new ComponentAdapter() {
      override def componentResized(evt: ComponentEvent): Unit = {
        super.componentResized(evt)
        consolePanel.setDividerLocation((getHeight.asInstanceOf[Double] * 0.65).toInt)
      }
    })

    output.setComponentPopupMenu(outputContextMenu)
    input.setComponentPopupMenu(inputContextMenu)

    input.setTabSize(2)
    output.setTabSize(2)
  }

  private def runCode(): Unit = {
    val cmd = input.getText.trim

    // remove repeated identical commands from history (Isaac B 6/26/25)
    cmdHistory = cmd +: cmdHistory.filter(_ != cmd)
    cmdHistoryIndex = -1

    input.setText("")
    input.setCaretPosition(0)
    input.requestFocus()

    output.append(">> " + cmd + "\n")

    try {
      evalStringified match {
        case Some(f) => output.append(f(cmd) + "\n")
        case None => output.append("This extension has not been properly initialized yet.\n")
      }
    } catch {
      case e: ExtensionException => {
        e.getCause match {
          case t: TargetLanguageErrorException => output.append(t.getLongMessage)
          case _ => throw e
        }
      }
    }
  }

  override def syncTheme(): Unit = {
    getContentPane.setBackground(InterfaceColors.dialogBackground())

    sp1.setBackground(InterfaceColors.dialogBackground())
    sp2.setBackground(InterfaceColors.dialogBackground())

    output.syncTheme()
    input.syncTheme()
    runButton.syncTheme()
    clearCodeAreaButton.syncTheme()
    clearHistoryAreaButton.syncTheme()

    outputContextMenu.syncTheme()
    inputContextMenu.syncTheme()
  }
}
