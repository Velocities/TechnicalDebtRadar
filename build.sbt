ThisBuild / scalaVersion := "3.8.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "technical-debt-radar",
    version := "0.1.0",

    // The analyzer shells out to `git`, so run in a forked JVM.
    run / fork := true,

    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    ),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),

    // --- Packaging: single runnable JAR via sbt-assembly ---
    // `@main def radar` compiles to a class named `radar`.
    assembly / mainClass := Some("radar"),
    assembly / assemblyJarName := "technical-debt-radar.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class")          => MergeStrategy.discard
      case p if p.endsWith("/module-info.class")  => MergeStrategy.discard
      case "META-INF/MANIFEST.MF"                 => MergeStrategy.discard
      case other =>
        val old = (assembly / assemblyMergeStrategy).value
        old(other)
    }
  )
