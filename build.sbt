name         := "language-library"
organization := "org.nlogo.languagelibrary"
version      := "3.3.3"
isSnapshot   := true

scalaVersion          := "3.7.0"
scalacOptions        ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings", "-release", "17", "-Wunused:linted")
Compile / scalaSource := baseDirectory.value / "src"

resolvers += "netlogo" at "https://dl.cloudsmith.io/public/netlogo/netlogo/maven/"

def cclArtifacts(path: String): String =
  s"https://s3.amazonaws.com/ccl-artifacts/$path"

libraryDependencies ++= Seq(
  "org.nlogo"          % "netlogo"        % "7.0.0-2486d1e"
, "org.json4s"        %% "json4s-jackson" % "4.0.7"
// not used by this library, but needed for NetLogo
, "org.jogamp.jogl" % "jogl-all" % "2.4.0" from cclArtifacts("jogl-all-2.4.0.jar")
, "org.jogamp.gluegen" % "gluegen-rt" % "2.4.0" from cclArtifacts("gluegen-rt-2.4.0.jar")
)

publishTo := { Some("Cloudsmith API" at "https://maven.cloudsmith.io/netlogo/language-library/") }
