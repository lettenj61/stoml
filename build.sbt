scalaVersion := "2.11.7"
enablePlugins(ScalaJSPlugin)

lazy val stoml = crossProject
  .settings(

    name := "stoml",
    organization := "com.github.jvican",
    version := "0.1",
    scalaVersion := "2.11.7",

    libraryDependencies ++= Vector(
      "com.lihaoyi" %%% "fastparse" % "0.3.4",
      "org.scalacheck" %%% "scalacheck" % "1.12.5" % "test"
    ),

    licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/MIT")),

    sonatypeProfileName := "com.github.jvican",

    pomExtra in Global := {
      <url>https://github.com/jvican/stoml.git</url>
      <scm>
        <developerConnection>scm:git:git@github.com:jvican</developerConnection>
        <url>https://github.com/jvican/stoml.git</url>
        <connection>scm:git:git@github.com:jvican/stoml.git</connection>
      </scm>
      <developers>
        <developer>
          <id>jvican</id>
          <name>Jorge Vicente Cantero</name>
          <url>https://github.com/jvican</url>
        </developer>
      </developers>
    }
  ).jvmSettings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  ).jsSettings(
    scalaJSUseRhino in Global := false,
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0-RC4" % "test"
  )

lazy val stomlJS = stoml.js
lazy val stomlJVM = stoml.jvm
