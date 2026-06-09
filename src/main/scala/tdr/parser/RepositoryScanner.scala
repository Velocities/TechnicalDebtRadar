package tdr.parser

import java.io.File
import scala.io.Source
import scala.util.Using
import tdr.types.ScannedFile

// This class is responsible for scanning the repository and returning a list of ScannedFile objects
// These ScannedFile objects contain info about the file and its contents (e.g. lines of code, imports, classes, functions)
object RepositoryScanner:

  /** Source extensions we attempt to analyze. Add more as parsers improve. */
  val defaultExtensions: Set[String] =
    Set("scala", "java", "py", "js", "ts", "jsx", "tsx", "go", "rb", "kt", "rs", "cs")

  /** Directories we never descend into. */
  private val ignoredDirs: Set[String] =
    Set(".git", "target", "node_modules", "build", "dist", ".idea", ".bsp", "venv", ".venv")

  /** Sequential scan. Kept for simple callers and tests; the CLI uses the
    * parallel path in [[tdr.parser.GraphBuilder]].
    */
  def scan(root: File, extensions: Set[String] = defaultExtensions): List[ScannedFile] =
    sourceFiles(root, extensions).map(f => scanFile(root, f))

  /** Cheap directory walk: lists candidate source files without reading them.
    * Reading/parsing each file (the expensive, I/O-bound part) is done
    * separately so it can be parallelized across files.
    */
  def sourceFiles(root: File, extensions: Set[String] = defaultExtensions): List[File] =
    walk(root).filter(f => extensions.contains(extensionOf(f.getName)))

  /** Reads and parses a single file. Blocking I/O — safe to run on a virtual
    * thread.
    */
  def scanFile(root: File, file: File): ScannedFile =
    val content = Using(Source.fromFile(file, "UTF-8"))(_.getLines().toList)
      .getOrElse(Nil)
    val loc = content.count(_.trim.nonEmpty)
    // Tree-sitter parses the whole file into a concrete syntax tree, from which
    // we read imports plus the classes (with their methods/fields) and the
    // top-level functions. The extension selects the language grammar.
    val parsed = TreeSitterParser.parse(content.mkString("\n"), extensionOf(file.getName))
    ScannedFile(relativePath(root, file), loc, parsed.imports, parsed.classes, parsed.functions)

  /**
    * Walks the directory and returns a list of files.
    */
  private def walk(dir: File): List[File] =
    val entries = Option(dir.listFiles()).map(_.toList).getOrElse(Nil)
    entries.flatMap { f =>
      if f.isDirectory then
        if ignoredDirs.contains(f.getName) then Nil else walk(f)
      else List(f)
    }

/**
 * Returns the relative path of the file from the root of the repository.
 */
  private def relativePath(root: File, file: File): String =
    root.toPath.relativize(file.toPath).toString.replace('\\', '/')

  private def extensionOf(name: String): String =
    val i = name.lastIndexOf('.')
    if i < 0 then "" else name.substring(i + 1).toLowerCase
