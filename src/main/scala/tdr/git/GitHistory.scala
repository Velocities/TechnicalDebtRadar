package tdr.git

import java.io.File
import scala.sys.process.*
import scala.util.Try

/** Per-file Git metrics. `churn` is the number of commits that touched the
  * file; `contributors` is the set of distinct author names.
  */
final case class FileHistory(path: String, churn: Int, contributors: Set[String])

object GitHistory:

  /** Run `git log` over `repoDir` and aggregate churn / contributors per file.
    *
    * Returns an empty map when the directory is not a Git repository or when
    * `git` is unavailable, so callers can degrade gracefully.
    */
  def analyze(repoDir: File): Map[String, FileHistory] =
    rawLog(repoDir).map(parse).getOrElse(Map.empty)

  /** Pretty format emits, per commit: a line `\u0001<author>` followed by the
    * file paths changed in that commit (via --name-only).
    */
  private def rawLog(repoDir: File): Try[String] = Try {
    val cmd = Seq(
      "git",
      "-C",
      repoDir.getAbsolutePath,
      "log",
      "--no-merges",
      "--pretty=format:\u0001%an",
      "--name-only"
    )
    cmd.!!
  }

  def parse(log: String): Map[String, FileHistory] =
    val acc = scala.collection.mutable.Map[String, (Int, Set[String])]()
    var currentAuthor = ""
    for line <- log.linesIterator do
      if line.startsWith("\u0001") then currentAuthor = line.drop(1).trim
      else
        val path = line.trim
        if path.nonEmpty then
          val (churn, authors) = acc.getOrElse(path, (0, Set.empty[String]))
          acc(path) = (churn + 1, authors + currentAuthor)
    acc.map { case (path, (churn, authors)) =>
      path -> FileHistory(path, churn, authors)
    }.toMap
