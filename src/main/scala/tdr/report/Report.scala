package tdr.report

import tdr.ir.ArchitectureGraph
import tdr.analysis.{CycleDetector, RiskCalculator, RiskScore}

/** Renders the Version 1 text report: hotspots (ranked risk) and circular
  * dependency detection. Returns a string so it is easy to test and to later
  * redirect to a file or other formats.
  */
object Report:

  def render(graph: ArchitectureGraph, topN: Int = 15): String =
    val sb = StringBuilder()
    sb.append("Technical Debt Radar - Report\n")
    sb.append("=" * 40).append("\n\n")

    sb.append(s"Files analyzed: ${graph.nodes.size}\n")
    sb.append(s"Dependency edges: ${graph.edges.values.map(_.size).sum}\n\n")

    sb.append(renderHotspots(RiskCalculator.rank(graph), topN))
    sb.append("\n")
    sb.append(renderCycles(CycleDetector.findCycles(graph)))
    sb.toString

  private def renderHotspots(scores: List[RiskScore], topN: Int): String =
    val sb = StringBuilder()
    sb.append(s"Hotspots (top $topN by risk)\n")
    sb.append("-" * 40).append("\n")
    if scores.isEmpty then sb.append("  (no files found)\n")
    else
      sb.append(f"  ${"risk"}%8s  ${"loc"}%6s  ${"churn"}%6s  ${"auth"}%5s  ${"coupl"}%6s  file\n")
      for s <- scores.take(topN) do
        sb.append(
          f"  ${s.score}%8.2f  ${s.loc}%6d  ${s.churn}%6d  ${s.contributors}%5d  ${s.coupling}%6d  ${s.path}\n"
        )
    sb.toString

  private def renderCycles(cycles: List[List[String]]): String =
    val sb = StringBuilder()
    sb.append("Circular dependencies\n")
    sb.append("-" * 40).append("\n")
    if cycles.isEmpty then sb.append("  None detected.\n")
    else
      for (cycle, i) <- cycles.zipWithIndex do
        sb.append(s"  [${i + 1}] ${cycle.mkString(" -> ")} -> ${cycle.head}\n")
    sb.toString
