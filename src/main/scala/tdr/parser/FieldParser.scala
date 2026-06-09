package tdr.parser

import scala.util.matching.Regex
import tdr.types.FieldEntity

/** Extracts field / property declarations from a single line of source code.
  *
  * Heuristic and regex-based for Version 1. Field syntax is highly
  * language-specific, so this takes the file extension and applies the matching
  * convention. It is only meant to be called on lines that sit *directly* inside
  * a type body (see [[StructureParser]]); callers handle the nesting.
  */
object FieldParser:

  // val/var/let/const name [: Type]   (Scala, Kotlin, JS, TS)
  private val binding: Regex =
    """\b(?:val|var|let|const)\s+([A-Za-z_]\w*)\s*(?::\s*([A-Za-z_][\w<>\[\].]*))?""".r
  // [modifiers] name : Type           (Scala, Kotlin, TS, typed Python)
  private val typedColon: Regex =
    """^\s*(?:(?:public|private|protected|internal|readonly|final|static|pub|override|lateinit)\s+)*([A-Za-z_]\w*)\s*:\s*([A-Za-z_][\w<>\[\].]*)""".r
  // [modifiers]+ Type name [;=]        (Java, C#)
  private val typeFirst: Regex =
    """^\s*(?:(?:public|private|protected|internal|readonly|final|static|volatile|transient|const)\s+)+([A-Za-z_][\w<>\[\].]*)\s+([A-Za-z_]\w*)\s*[;=]""".r
  // name [: Type] = ...               (Python / JS class attributes)
  private val assign: Regex =
    """^\s*([A-Za-z_]\w*)\s*(?::\s*([A-Za-z_][\w<>\[\].]*))?\s*=(?!=)""".r
  // Name Type `tag`                    (Go struct fields)
  private val goField: Regex =
    """^\s*([A-Za-z_]\w*)\s+([A-Za-z_][\w.\[\]*]*)\s*(?:`[^`]*`)?\s*$""".r
  // attr_accessor :a, :b              (Ruby)
  private val rubyAttr: Regex = """\battr_(?:accessor|reader|writer)\s+(.+)""".r
  private val rubySymbol: Regex = """:([A-Za-z_]\w*)""".r

  /** Fields declared on `line` (usually 0 or 1; Ruby `attr_accessor` may yield several). */
  def fieldsIn(line: String, ext: String): List[FieldEntity] =
    ext match
      case "rb" =>
        rubyAttr.findFirstMatchIn(line).toList.flatMap { m =>
          rubySymbol.findAllMatchIn(m.group(1)).map(s => FieldEntity(s.group(1), "")).toList
        }
      case "go" =>
        goField.findFirstMatchIn(line).map(m => FieldEntity(m.group(1), m.group(2))).toList
      case _ =>
        generic(line).toList

  private def generic(line: String): Option[FieldEntity] =
    binding
      .findFirstMatchIn(line)
      .map(m => FieldEntity(m.group(1), Option(m.group(2)).getOrElse("")))
      .orElse(typedColon.findFirstMatchIn(line).map(m => FieldEntity(m.group(1), m.group(2))))
      .orElse(typeFirst.findFirstMatchIn(line).map(m => FieldEntity(m.group(2), m.group(1))))
      .orElse(assign.findFirstMatchIn(line).map(m => FieldEntity(m.group(1), Option(m.group(2)).getOrElse(""))))
