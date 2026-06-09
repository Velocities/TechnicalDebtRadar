package tdr.parser

import scala.util.matching.Regex
import tdr.types.ClassEntity

/** Extracts type declarations (classes, traits, interfaces, structs, enums, ...)
  * from a single line of source code.
  *
  * Heuristic and regex-based for Version 1, mirroring [[ImportParser]]. It covers
  * the type-declaration keywords across the languages we scan.
  *
  * Note: extracting the methods/fields that live *inside* a type body is out of
  * scope here — line-based scanning has no body context — so
  * `ClassEntity.methods` and `ClassEntity.fields` are left empty until structural
  * (Tree-sitter) parsing lands. This parser only recovers the declared name.
  */
object ClassParser:

  /** Each pattern captures the declared type name in group 1. The keyword sits
    * directly before the name in every supported language except Go, which is
    * handled by its own `type Name struct/interface` form.
    */
  private val declarations: List[Regex] = List(
    """\bclass\s+([A-Za-z_]\w*)""".r,     // Scala, Java, Kotlin, Python, Ruby, JS, TS, C#
    """\bobject\s+([A-Za-z_]\w*)""".r,    // Scala, Kotlin
    """\btrait\s+([A-Za-z_]\w*)""".r,     // Scala, Rust
    """\binterface\s+([A-Za-z_]\w*)""".r, // Java, Kotlin, TS, C#
    """\benum\s+([A-Za-z_]\w*)""".r,      // Java, Rust, C#, TS
    """\bstruct\s+([A-Za-z_]\w*)""".r,    // Rust, C#
    """\bunion\s+([A-Za-z_]\w*)""".r,     // Rust
    """\brecord\s+([A-Za-z_]\w*)""".r,    // Java, C#
    """\bmodule\s+([A-Za-z_]\w*)""".r,    // Ruby, TS
    """\btype\s+([A-Za-z_]\w*)\s+(?:struct|interface)\b""".r // Go
  )

  /** Reserved words that may be captured as a "name" when one keyword follows
    * another (e.g. Kotlin's `enum class Foo` yields both `class` and `Foo`).
    */
  private val keywords: Set[String] =
    Set("class", "object", "trait", "interface", "enum", "struct", "union", "record", "module", "type")

  /** All type declarations found on `line` (usually 0 or 1). */
  def classesIn(line: String): List[ClassEntity] =
    declarations
      .flatMap(_.findAllMatchIn(line).map(_.group(1)))
      .filterNot(keywords.contains)
      .distinct
      .map(name => ClassEntity(name, methods = Nil, fields = Nil))
