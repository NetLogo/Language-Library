package org.nlogo.languagelibrary.prims

import org.nlogo.api.{ Argument, Command, Context }
import org.nlogo.core.Syntax

import org.nlogo.languagelibrary.Subprocess

object EnableDebug extends Command {
  override def getSyntax: Syntax = {
    Syntax.commandSyntax(right = List())
  }

  override def perform(args: Array[Argument], context: Context): Unit = {
    Subprocess.logger.enableDebug()
  }
}
