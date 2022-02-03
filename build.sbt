organization := "org.nlogo.langextension"
version := "0.2"
name := "lang-extension-lib"

isSnapshot := true

scalaVersion := "2.12.12"

Compile / scalaSource := baseDirectory.value / "src"

resolvers += "netlogo" at "https://dl.cloudsmith.io/public/netlogo/netlogo/maven/"

libraryDependencies ++= Seq(
  "org.nlogo" % "netlogo" % "6.2.0",
  "org.json4s" %% "json4s-jackson" % "3.5.3",
)

publishTo := { Some("Cloudsmith API" at "https://maven.cloudsmith.io/netlogo/netlogoextensionlanguageserverlibrary/") }
