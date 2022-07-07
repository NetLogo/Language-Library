package org.nlogo.languagelibrary

import org.nlogo.api.ExtensionException

import java.awt.event._
import java.awt.{BorderLayout, Dimension}
import javax.swing._

/**
 * A ShellWindow lets users interact with the target language through an
 * interactive shell/REPL. It handles most all of its own functionality.
 * The extension code only needs to manage its lifecycle and set an
 * `evalStringified` function to be called when evaluating input code.
 */
class ShellWindow extends JFrame with KeyListener with ActionListener {
  // **functional state**
  private var evalStringified: Option[(String) => String] = None
  private var cmdHistory: Seq[String] = Seq()
  private var cmdHistoryIndex = 0
  private var cmdHistoryFirst = true
  private var menuItemCallbacks: Map[String, (ActionEvent) => Unit] = Map()

  // **Swing objects**
  private val consolePanel: JSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT)
  val output = new JTextArea()
  val input = new JTextArea()

  val bottomContainer = new JPanel()
  val runButton = new JButton("Run")
  val clearCodeAreaButton = new JButton("Clear Code")

  val topContainer = new JPanel()
  val clearHistoryAreaButton = new JButton("Clear History")

  val contextMenu = new JPopupMenu("Edit")

  // **Setup**
  initPanels()
  clearHistoryAreaButton.addActionListener((_: ActionEvent) => {output.setText("")})
  clearCodeAreaButton.addActionListener((_: ActionEvent) => {input.setText("")})
  runButton.addActionListener((_: ActionEvent) => {runCode()})
  addRightClickMenuItem("Clear Output Text", (e: ActionEvent) => {
    output.setText("")
  })
  addRightClickMenuItem("Clear Input Text", (e: ActionEvent) => {
    input.setText("")
  })

  output.setText(
    "Usage:\n\n"
      + "Write commands in the lower area and hit Ctrl-Enter to submit them.\n"
      + "Use page up/down to recall previously submitted commands.\n\n"
  )

  // ---------------------Getters and Setters----------------------------------

  def setEvalStringified(_evalStringified: Option[(String) => String]): Unit = {
    evalStringified = _evalStringified
  }

  def getEvalStringified: Option[(String) => String] = evalStringified

  def getCmdHistory: Seq[String] = cmdHistory

  // -------------------------Helpers------------------------------------------

  private def initPanels(): Unit = {
    input.addKeyListener(this)

    val sp1 = new JScrollPane(output)
    sp1.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
    consolePanel.setTopComponent(sp1)

    val sp2 = new JScrollPane(input)
    sp2.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
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

    output.setComponentPopupMenu(contextMenu)
    input.setComponentPopupMenu(contextMenu)
    output.setInheritsPopupMenu(true)
    input.setInheritsPopupMenu(true)

    input.setTabSize(2)
    output.setTabSize(2)

  }

  private def addRightClickMenuItem(label: String, callback: (ActionEvent) => Unit): Unit = {
    val item: JMenuItem = new JMenuItem(label)
    item.addActionListener(this)
    menuItemCallbacks = menuItemCallbacks + (label -> callback)
    contextMenu.add(item)
  }

  // -------------------------Listeners----------------------------------------

  override def actionPerformed(e: ActionEvent) {
    val command = e.getActionCommand
    menuItemCallbacks get command match {
      case Some(callback) => callback(e)
      case None =>
    }
  }

  override def keyTyped(ke: KeyEvent): Unit = {
  }

  override def keyPressed(ke: KeyEvent): Unit = {
    if (ke.isControlDown && ke.getKeyCode == KeyEvent.VK_ENTER) {
      runCode()
    }
  }

  override def keyReleased(ke: KeyEvent): Unit = {
    if (ke.getKeyCode == KeyEvent.VK_PAGE_DOWN || ke.getKeyCode == KeyEvent.VK_PAGE_UP) {
      if (cmdHistory.nonEmpty) {
        if (ke.getKeyCode == KeyEvent.VK_PAGE_UP) {
          if (!cmdHistoryFirst) {
            cmdHistoryIndex -= 1
          }
          if (cmdHistoryIndex < 0) {
            cmdHistoryIndex = cmdHistory.size - 1
          }
        } else {
          cmdHistoryIndex += 1
          if (cmdHistoryIndex >= cmdHistory.size) {
            cmdHistoryIndex = 0
          }
        }
        cmdHistoryFirst = false
        input.setText(cmdHistory(cmdHistoryIndex))
      }
    }
  }

  private def runCode(): Unit = {
    val cmd = input.getText.trim

    if (cmdHistory.isEmpty || cmdHistory.last != cmd) { // ignore repeated identical commands
      cmdHistory :+= cmd
      cmdHistoryIndex = cmdHistory.size - 1
    }
    cmdHistoryFirst = true

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

}
