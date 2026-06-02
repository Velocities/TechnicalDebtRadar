import java.io.File
import tdr.parser.GraphBuilder
import tdr.report.Report

/** Entry point for the Technical Debt Radar CLI.
  *
  * Usage: `radar [path-to-repo]` (defaults to the current directory).
  *
  * Pipeline (see README "Core Architecture"):
  *   Repository -> Scanner/Parser -> Architecture IR -> Analysis -> Report
  */
@main def radar(args: String*): Unit =
  val repoDir = File(args.headOption.getOrElse(".")).getAbsoluteFile

  if !repoDir.isDirectory then
    System.err.println(s"Not a directory: $repoDir")
    sys.exit(2)

  println(s"Analyzing ${repoDir.getPath} ...\n")
  val graph = GraphBuilder.build(repoDir)
  println(Report.render(graph))
