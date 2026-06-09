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
      // Tree-sitter (JNI) core + per-language grammars. The grammar artifacts
      // bundle native shared libraries for x86_64/aarch64 Linux/macOS/Windows,
      // so no system-level tree-sitter install is required.
      "io.github.bonede" % "tree-sitter"            % "0.25.3",
      "io.github.bonede" % "tree-sitter-scala"      % "0.23.3",
      "io.github.bonede" % "tree-sitter-java"       % "0.23.4",
      "io.github.bonede" % "tree-sitter-python"     % "0.23.4",
      "io.github.bonede" % "tree-sitter-go"         % "0.23.3",
      "io.github.bonede" % "tree-sitter-rust"       % "0.23.1",
      "io.github.bonede" % "tree-sitter-javascript" % "0.23.1",
      "io.github.bonede" % "tree-sitter-typescript" % "0.23.2",
      "io.github.bonede" % "tree-sitter-ruby"       % "0.23.1",
      "io.github.bonede" % "tree-sitter-kotlin"     % "0.3.8.1",
      "io.github.bonede" % "tree-sitter-c-sharp"    % "0.23.1",
      "org.scalatest"   %% "scalatest"              % "3.2.19" % Test
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
