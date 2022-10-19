package org.nlogo.languagelibrary

import java.lang.{ Boolean => JavaBoolean, Double => JavaDouble }

import org.json4s.JValue
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.parse

import org.nlogo.agent.{ Agent, AgentSet }
import org.nlogo.api.ExtensionException
import org.nlogo.core.{ Dump, LogoList, Nobody }

class Convert(val extensionLongName: String) {
  def toJson(x: AnyRef): JValue = x match {
    case j: JValue => j
    case s: String => JString(s)
    case l: LogoList => l.map(toJson)
    case b: JavaBoolean => if (b) JBool.True else JBool.False
    case Nobody => JNothing
    case agent: Agent => agentToJson(agent)
    case set: AgentSet => set.toLogoList.map(agent => agentToJson(agent.asInstanceOf[Agent]))
    case o => parse(Dump.logoObject(o, readable = true, exporting = false))
  }

  private def agentToJson(agent: Agent) : JValue = {
    var obj : JObject = org.json4s.JObject()
    agent.variables.indices.foreach(i => {
      val name = agent.variableName(i)
      val value = agent.getVariable(i) match {
        case set : AgentSet => toJson(set.printName) // <plural agentset name>
        case agent : Agent => toJson(agent.toString) // <singular agentset name> <id>
        case other : AnyRef => toJson(other)
      }
      obj = obj ~ (name -> value)
    })
    obj
  }

  def toNetLogo(x: JValue): AnyRef = x match {
    case JNothing => Nobody
    case JNull => Nobody
    case JString(s) => s
    case JDouble(num) => ensureValidNum(num): JavaDouble
    case JDecimal(num) => ensureValidNum(num.toDouble): JavaDouble
    case JLong(num) => ensureValidNum(num.toDouble): JavaDouble
    case JInt(num) => ensureValidNum(num.toDouble): JavaDouble
    case JBool(value) => value: JavaBoolean
    case JObject(obj) => LogoList.fromVector(obj.map(f => LogoList(f._1, toNetLogo(f._2))).toVector)
    case JArray(arr) => LogoList.fromVector(arr.map(toNetLogo).toVector)
    case JSet(set) => LogoList.fromVector(set.map(toNetLogo).toVector)
  }

  private def ensureValidNum(d: Double): Double = d match {
    case x if x.isInfinite => throw new ExtensionException(extensionLongName + " reported a number too large for NetLogo.")
    case x if x.isNaN => throw new ExtensionException(extensionLongName + " reported a non-numeric value from a mathematical operation.")
    case x => x
  }

}
