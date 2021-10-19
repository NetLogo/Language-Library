package org.me

import java.awt.event._
import java.awt.{BorderLayout, Dimension}
import javax.swing._

class ShellWindow extends JFrame with KeyListener with ActionListener {
  var eval_stringified: Option[(String) => String] = None
  var cmdHistory: Seq[String] = Seq()
  private var cmdHistoryIndex = 0;
  private var cmdHistoryFirst = true;
  private var menuItemCallbacks: Map[String, (ActionEvent) => Unit] = Map()

  private val consolePanel: JSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT)
  val output = new JTextArea()
  val input = new JTextArea()
  val contextMenu = new JPopupMenu("Edit")

  initPanels()
  addRightClickMenuItem("Clear Output Text", (e: ActionEvent) => {
    output.setText("")
  })
  addRightClickMenuItem("Clear Input Text", (e: ActionEvent) => {
    input.setText("")
  })


  private def initPanels(): Unit = {
    input.addKeyListener(this)

    val sp1 = new JScrollPane(output)
    sp1.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
    consolePanel.setTopComponent(sp1)

    val sp2 = new JScrollPane(input)
    sp2.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
    consolePanel.setBottomComponent(sp2)

    this.getContentPane.setLayout(new BorderLayout)
    this.getContentPane.add(consolePanel, BorderLayout.CENTER)
    this.setMinimumSize(new Dimension(555, 650))
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

    output.setText(
      "Usage:\n\n"
        + "Write commands in the lower area and hit Ctrl-Enter to submit them.\n"
        + "Use page up/down to recall previously submitted commands.\n\n"
    )
  }

  def addRightClickMenuItem(label: String, callback: (ActionEvent) => Unit): Unit = {
    val item: JMenuItem = new JMenuItem(label)
    item.addActionListener(this)
    menuItemCallbacks = menuItemCallbacks + (label -> callback)
    contextMenu.add(item)
  }

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
      val cmd = input.getText.trim

      cmdHistory :+= cmd
      cmdHistoryIndex = cmdHistory.size - 1
      cmdHistoryFirst = true

      input.setText("")
      input.setCaretPosition(0)
      input.requestFocus()

      output.append(">> " + cmd + "\n")

      eval_stringified match {
        case Some(f) => output.append(f(cmd) + "\n")
        case None => output.append("This extension has not been properly initialized yet.\n")
      }
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
        cmdHistoryFirst = false;
        input.setText(cmdHistory(cmdHistoryIndex))
      }
    }
  }
}

