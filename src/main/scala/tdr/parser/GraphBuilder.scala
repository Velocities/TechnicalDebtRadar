package tdr.parser

import java.io.File
import tdr.git.{FileHistory, GitHistory}
import tdr.ir.{ArchitectureGraph, FileNode}

/** Assembles the Architecture IR from the three Version 1 data sources:
  * the file system (scanner), Git history, and extracted imports.
  */
object GraphBuilder:

  def build(repoDir: File): ArchitectureGraph =
    val scanned = RepositoryScanner.scan(repoDir)
    val history = GitHistory.analyze(repoDir)
    build(scanned, history)

  private[parser] def build(
      scanned: List[ScannedFile],
      history: Map[String, FileHistory]
  ): ArchitectureGraph =
    val nodes: Map[String, FileNode] = scanned.map { f =>
      val h = history.get(f.relativePath)
      f.relativePath -> FileNode(
        path = f.relativePath,
        loc = f.loc,
        churn = h.map(_.churn).getOrElse(0),
        contributors = h.map(_.contributors.size).getOrElse(0)
      )
    }.toMap

    val knownPaths = nodes.keySet
    val edges: Map[String, Set[String]] = scanned.map { f =>
      val resolved = f.imports.flatMap(resolve(_, knownPaths)) - f.relativePath
      f.relativePath -> resolved
    }.toMap

    ArchitectureGraph(nodes, edges)

  /** Best-effort resolution of an import target to a known file path.
    *
    * We normalize dotted module names to slash paths and look for a known file
    * whose path (sans extension) ends with the target. This is intentionally
    * approximate; precise resolution is a Future Goal (Tree-sitter).
    */
  private[parser] def resolve(target: String, knownPaths: Set[String]): Option[String] =
    val normalized = target.replace('.', '/').stripPrefix("./").stripPrefix("/")
    knownPaths.find { path =>
      val withoutExt = path.substring(0, path.lastIndexOf('.').max(0))
      withoutExt.endsWith(normalized) || path.endsWith(normalized)
    }
