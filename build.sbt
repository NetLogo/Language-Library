name         := "language-library"
organization := "org.nlogo.languagelibrary"
version      := "3.3.0"
isSnapshot   := true

scalaVersion          := "3.7.0"
scalacOptions        ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings", "-release", "17", "-Wunused:linted")
Compile / scalaSource := baseDirectory.value / "src"

resolvers += "netlogo" at "https://dl.cloudsmith.io/public/netlogo/netlogo/maven/"

libraryDependencies ++= Seq(
  "org.nlogo"          % "netlogo"        % "7.0.0-beta2-cfa301b"
, "org.json4s"        %% "json4s-jackson" % "4.0.7"
// not used by this library, but needed for NetLogo
, "org.jogamp.jogl" % "jogl-all" % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0/jar/jogl-all.jar"
, "org.jogamp.gluegen" % "gluegen-rt" % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0/jar/gluegen-rt.jar"
)

publishTo := { Some("Cloudsmith API" at "https://maven.cloudsmith.io/netlogo/language-library/") }
