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
      "-unchecked",
      // Requires JDK 21+: the analyzer uses virtual threads (JEP 444) for
      // parallel, blocking file I/O. This sets the bytecode/API floor.
      "-release:21"
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
