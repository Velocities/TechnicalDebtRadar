package tdr.parser

/** Extracts import targets from a single line of source code.
  *
  * This is deliberately heuristic and regex-based for Version 1. The README's
  * "Future Goals" call for proper Tree-sitter parsing; until then we cover the
  * common `import` / `from ... import` / `require(...)` forms across languages.
  */
object ImportParser:

  private val patterns: List[scala.util.matching.Regex] = List(
    // Scala / Java / Kotlin: `import a.b.c`
    """^\s*import\s+(?:static\s+)?([\w.]+)""".r,
    // Python: `from a.b import c`
    """^\s*from\s+([\w.]+)\s+import\b""".r,
    // Python: `import a.b`
    """^\s*import\s+([\w.]+)""".r,
    // JS / TS: `import ... from 'x'` or `import 'x'`
    """import\s+(?:.*?\s+from\s+)?['"]([^'"]+)['"]""".r,
    // CommonJS: `require('x')`
    """require\(\s*['"]([^'"]+)['"]\s*\)""".r,
    // Go: `import "x"`
    """^\s*"([\w./]+)"\s*$""".r
  )

  /** All import targets referenced on `line` (usually 0 or 1). */
  def importsIn(line: String): Set[String] =
    patterns.flatMap(_.findAllMatchIn(line).map(_.group(1))).toSet
