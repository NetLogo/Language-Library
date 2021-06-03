package org.me

import java.awt.event._
import java.awt.{BorderLayout, Dimension}
import javax.swing._

class ShellWindow(eval_stringified: (String) => AnyRef) extends JFrame with KeyListener with ActionListener {
  var cmdHistory : Seq[String] = Seq()
  private var cmdHistoryIndex = 0;
  private var cmdHistoryFirst = true;

  private val consolePanel: JSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT)
  val output = new JTextArea()
  val input = new JTextArea()
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

  val contextMenu = new JPopupMenu("Edit")
  contextMenu.add(makeMenuItem("Clear all"))
  contextMenu.add(makeMenuItem("Clear Window"))
  contextMenu.add(makeMenuItem("Clear History"))
  contextMenu.add(makeMenuItem("Save History to File"))
  contextMenu.add(makeMenuItem("Load Histroy from File"))
  output.setComponentPopupMenu(contextMenu)
  input.setComponentPopupMenu(contextMenu)
  output.setInheritsPopupMenu(true)
  input.setInheritsPopupMenu(true)


  override def actionPerformed(e: ActionEvent) {
    println("Action Performed")
    println(e)
  }

  /**
   * A method to create context menu
   *
   * @param label Name of the context item added
   * @return A menu item
   */
  private def makeMenuItem (label: String): JMenuItem = {
    val item: JMenuItem = new JMenuItem (label)
    item.addActionListener (this)
    item
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
      val res = eval_stringified(cmd)
      output.append(res.toString + "\n")
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

