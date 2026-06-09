package tdr.parser

import java.io.File
import scala.concurrent.{ExecutionContext, Future, blocking}
import tdr.concurrent.Concurrency
import tdr.git.{FileHistory, GitHistory}
import tdr.ir.{ArchitectureGraph, FileNode}
import tdr.types.ScannedFile

/** Assembles the Architecture IR from the three Version 1 data sources:
  * the file system (scanner), Git history, and extracted imports.
  *
  * The two slow stages — reading every file and shelling out to `git log` —
  * are independent and run concurrently on virtual threads. Per-file reads and
  * per-file import resolution are also fanned out across files.
  */
object GraphBuilder:

  /** Blocking convenience entry point. Manages its own virtual-thread executor
    * and blocks the calling thread (only) until the graph is ready. */
  def build(repoDir: File): ArchitectureGraph =
    Concurrency.withVirtualThreads(buildAsync(repoDir))

  /** Asynchronous pipeline. Git history and file scanning are launched up front
    * so they overlap; the graph is assembled once both complete.
    */
  def buildAsync(repoDir: File)(using ExecutionContext): Future[ArchitectureGraph] =
    // Launched eagerly -> these two run concurrently.
    // Kick off analysis of git history in the background (runs on a virtual thread)
    val historyF: Future[Map[String, FileHistory]] =
      Future {
        blocking(
          GitHistory.analyze(repoDir)
        )
      }

    // Gather all source files to scan, then scan each file in parallel (also on virtual threads)
    val scannedF: Future[List[ScannedFile]] =
      val files = RepositoryScanner.sourceFiles(repoDir)
      Future.traverse(files) { file =>
        Future {
          blocking(
            RepositoryScanner.scanFile(repoDir, file)
          )
        }
      }

    // Once both the git history and file scanning complete, assemble the architecture graph (edges in parallel)
    for
      history <- historyF
      scanned <- scannedF
      graph   <- assembleAsync(
        scanned = scanned,
        history = history
      )
    yield graph

  /** Builds nodes (cheap) then resolves edges in parallel across files. */
  private def assembleAsync(
      // Scanned source files with LOC and import targets
      scanned: List[ScannedFile],
      // Git history data (churn and contributors) for each file
      history: Map[String, FileHistory]
  )(using ExecutionContext): Future[ArchitectureGraph] =
    // First, build the set of nodes containing per-file metrics (combining scanned source files and git history)
    val nodes = buildNodes(scanned, history)
    // The full set of file paths available in this repository (for resolving imports)
    val knownPaths = nodes.keySet
    // For each scanned file, resolve its edges in parallel:
    Future
      .traverse(scanned)(scannedFile => Future(resolveEdges(scannedFile, knownPaths)))
      // Once all parallel import resolutions are done, assemble the ArchitectureGraph
      .map(edges => ArchitectureGraph(nodes, edges.toMap))

  /** 
    * Pure, synchronous assembler for building an ArchitectureGraph.
    * 
    * This is used in unit tests and for simple (non-parallel) callers.
    * 
    * - Builds the set of nodes by combining file scan metrics and git history.
    * - For each scanned file, attempts to resolve each import to a known file path.
    *   - Each edge in the graph indicates that the file imports (depends on) another resolved file.
    *   - Self-dependencies are explicitly removed.
    * - The resulting graph models per-file metrics and their dependency relationships.
    */
  def assemble(
      scanned: List[ScannedFile],
      history: Map[String, FileHistory]
  ): ArchitectureGraph =
    val nodes = buildNodes(scanned, history)
    val knownPaths = nodes.keySet

    // For each scanned file, attempt to resolve each import to another known file path, discarding self-imports.
    val edges = scanned.map(scannedFile => resolveEdges(scannedFile, knownPaths)).toMap

    ArchitectureGraph(nodes, edges)

  /** 
    * Combines data from scanned source files and Git history to produce per-file metrics in FileNode form.
    * 
    * For each scanned file:
    *   - Extracts LOC from scan
    *   - Looks up churn (commits) and number of contributors from Git history, defaulting to 0 if missing
    * 
    * Returns a map of relative paths to corresponding FileNode representing each file's metrics.
    */
  private def buildNodes(
      scanned: List[ScannedFile],
      history: Map[String, FileHistory]
  ): Map[String, FileNode] =
    scanned.map { scannedFile =>
      // Retrieve git history for this file if available
      val fileHistoryOpt = history.get(scannedFile.relativePath)
      scannedFile.relativePath -> FileNode(
        path = scannedFile.relativePath,
        loc = scannedFile.loc,
        churn = fileHistoryOpt.map(_.churn).getOrElse(0),
        contributors = fileHistoryOpt.map(_.contributors.size).getOrElse(0)
      )
    }.toMap

  /** Resolves a single file's import targets into a graph edge:
    * `(file path -> set of resolved dependency paths)`, with self-edges removed.
    */
  private def resolveEdges(
      scannedFile: ScannedFile,
      knownPaths: Set[String]
  ): (String, Set[String]) =
    val resolved = scannedFile.imports.flatMap(resolve(_, knownPaths)) - scannedFile.relativePath
    scannedFile.relativePath -> resolved

  /** Attempts to map a referenced dependency (as written in source code) to an actual source file path present in the project.
    * Converts import/module names to a comparable path format (e.g. "java.util.List" -> "java/util/List") and searches for
    * a matching file (with or without extension). Used to approximate dependency links.
    */
  private[parser] def resolve(target: String, knownPaths: Set[String]): Option[String] =
    val normalized = target.replace('.', '/').stripPrefix("./").stripPrefix("/")
    knownPaths.find { path =>
      val withoutExt = path.substring(0, path.lastIndexOf('.').max(0))
      withoutExt.endsWith(normalized) || path.endsWith(normalized)
    }
