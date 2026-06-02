package tdr.analysis

import tdr.ir.{ArchitectureGraph, FileNode}

/** A computed risk score for a single file, plus the inputs that produced it
  * (so reports can explain *why* a file is risky).
  */
final case class RiskScore(
    path: String,
    score: Double,
    loc: Int,
    churn: Int,
    contributors: Int,
    coupling: Int
)

object RiskCalculator:

  /** Heuristic risk score for a file. Higher means riskier.
    *
    * Intuition: big files that change often and are highly coupled are the most
    * dangerous. Files with a single contributor get a bus-factor penalty.
    * Values are damped with log1p so one huge dimension can't dominate.
    */
  def score(node: FileNode, coupling: Int): Double =
    val size = math.log1p(node.loc.toDouble)
    val change = math.log1p(node.churn.toDouble)
    val couple = math.log1p(coupling.toDouble)
    val busFactor = if node.contributors <= 1 then 1.5 else 1.0
    size * (1.0 + change) * (1.0 + couple) * busFactor

  /** Score every file in the graph, highest risk first. */
  def rank(graph: ArchitectureGraph): List[RiskScore] =
    graph.nodes.values.toList
      .map { node =>
        val coupling = graph.coupling(node.path)
        RiskScore(
          path = node.path,
          score = score(node, coupling),
          loc = node.loc,
          churn = node.churn,
          contributors = node.contributors,
          coupling = coupling
        )
      }
      .sortBy(-_.score)
