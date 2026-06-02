package tdr.parser

import java.io.File
import scala.io.Source
import scala.util.Using

/** A source file discovered on disk, with its lines of code and the raw import
  * targets extracted from it.
  */
final case class ScannedFile(
    relativePath: String,
    loc: Int,
    imports: Set[String]
)

object RepositoryScanner:

  /** Source extensions we attempt to analyze. Add more as parsers improve. */
  val defaultExtensions: Set[String] =
    Set("scala", "java", "py", "js", "ts", "jsx", "tsx", "go", "rb", "kt", "rs", "cs")

  /** Directories we never descend into. */
  private val ignoredDirs: Set[String] =
    Set(".git", "target", "node_modules", "build", "dist", ".idea", ".bsp", "venv", ".venv")

  def scan(root: File, extensions: Set[String] = defaultExtensions): List[ScannedFile] =
    walk(root)
      .filter(f => extensions.contains(extensionOf(f.getName)))
      .map(f => scanFile(root, f))

  private def walk(dir: File): List[File] =
    val entries = Option(dir.listFiles()).map(_.toList).getOrElse(Nil)
    entries.flatMap { f =>
      if f.isDirectory then
        if ignoredDirs.contains(f.getName) then Nil else walk(f)
      else List(f)
    }

  private def scanFile(root: File, file: File): ScannedFile =
    val content = Using(Source.fromFile(file, "UTF-8"))(_.getLines().toList)
      .getOrElse(Nil)
    val loc = content.count(_.trim.nonEmpty)
    val imports = content.flatMap(ImportParser.importsIn).toSet
    ScannedFile(relativePath(root, file), loc, imports)

  private def relativePath(root: File, file: File): String =
    root.toPath.relativize(file.toPath).toString.replace('\\', '/')

  private def extensionOf(name: String): String =
    val i = name.lastIndexOf('.')
    if i < 0 then "" else name.substring(i + 1).toLowerCase
