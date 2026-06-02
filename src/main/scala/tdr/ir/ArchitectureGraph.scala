package tdr.ir

/** A single file in the codebase together with the metrics we track for it.
  *
  * This is the language-independent unit of the Architecture IR described in
  * the README: every supported language is reduced to nodes like this.
  */
final case class FileNode(
    path: String,
    loc: Int,
    churn: Int,
    contributors: Int
)

/** The Architecture Intermediate Representation.
  *
  * `nodes` maps a file path to its metrics. `edges` maps a file path to the set
  * of file paths it depends on (i.e. imports). The graph is intentionally
  * language-agnostic so that the analysis engine never needs to know how the
  * edges were produced.
  */
final case class ArchitectureGraph(
    nodes: Map[String, FileNode],
    edges: Map[String, Set[String]]
):
  /** Files that `path` depends on (outgoing edges). */
  def dependencies(path: String): Set[String] =
    edges.getOrElse(path, Set.empty)

  /** Files that depend on `path` (incoming edges). */
  def dependents(path: String): Set[String] =
    edges.collect { case (from, tos) if tos.contains(path) => from }.toSet

  /** Total coupling: how many other files this file touches or is touched by. */
  def coupling(path: String): Int =
    (dependencies(path) ++ dependents(path)).size

object ArchitectureGraph:
  val empty: ArchitectureGraph = ArchitectureGraph(Map.empty, Map.empty)
