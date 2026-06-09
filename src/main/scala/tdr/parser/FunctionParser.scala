package tdr.parser

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex
import tdr.types.{FunctionEntity, ParameterEntity}

/** Extracts function/method declarations from a single line of source code.
  *
  * Heuristic and regex-based for Version 1, mirroring [[ImportParser]]. It covers
  * the common declaration forms across the languages we scan:
  *   - keyword forms: `def` / `fun` / `fn` / `func` / `function`
  *   - Go receiver methods: `func (r T) Name(...)`
  *   - JS/TS arrow functions bound to a name: `const name = (...) => ...`
  *   - Java / C# modifier forms: `public ReturnType name(...)`
  *   - Ruby parameter-less defs: `def name`
  *
  * Parameter typing convention: `name: Type` (Scala, Kotlin, TS, Rust, typed
  * Python) and `Type name` (Java, C#) are recovered correctly. Go's `name Type`
  * order is not distinguishable from `Type name` line-by-line, so Go parameter
  * names/types may be swapped — precise parsing is a Tree-sitter follow-up.
  */
object FunctionParser:

  private val mod =
    raw"(?:public|private|protected|internal|static|final|virtual|override|abstract|async|suspend|sealed|unsafe|open)"

  // keyword name(params) <tail>
  private val keywordDecl: Regex =
    """\b(?:def|fun|fn|func|function)\s+([A-Za-z_]\w*)\s*\(([^)]*)\)(.*)$""".r
  // Go receiver method: func (r T) Name(params) <tail>
  private val goMethod: Regex =
    """\bfunc\s*\([^)]*\)\s*([A-Za-z_]\w*)\s*\(([^)]*)\)(.*)$""".r
  // JS/TS arrow bound to a name: const|let|var name = (params) <tail> =>
  private val arrowFn: Regex =
    """\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s+)?\(([^)]*)\)([^=]*)=>""".r
  // Java / C#: <modifiers> ReturnType name(params)
  private val modifierDecl: Regex =
    (raw"\b" + mod + raw"(?:\s+" + mod + raw")*\s+([A-Za-z_][\w<>\[\].]*)\s+([A-Za-z_]\w*)\s*\(([^)]*)\)").r
  // Scala/Kotlin parameter-less def with a type or body: `def name: T`, `def name =`
  private val defNoParens: Regex =
    """\b(?:def|fun)\s+([A-Za-z_]\w*)\s*(?::\s*([^={;]+?))?\s*(?:[={]|$)""".r
  // Ruby parameter-less def: def name
  private val rubyNoParens: Regex =
    """\bdef\s+([A-Za-z_]\w*[?!=]?)\s*$""".r

  /** A return type expressed after the parameter list, as `: T` or `-> T`. */
  private val arrowOrColonReturn: Regex = """(?:->|:)\s*([^={;]+)""".r
  /** Go's trailing return type: `) T {`. */
  private val goReturn: Regex = """^\s*([A-Za-z_][\w.\[\]*<>,() ]*?)\s*\{\s*$""".r

  /** All function/method declarations found on `line` (usually 0 or 1). */
  def functionsIn(line: String): List[FunctionEntity] =
    val candidates: List[FunctionEntity] =
      keywordDecl.findFirstMatchIn(line).map(m =>
        FunctionEntity(m.group(1), parseParams(m.group(2)), tailReturn(m.group(3)))
      ).toList :::
        goMethod.findFirstMatchIn(line).map(m =>
          FunctionEntity(m.group(1), parseParams(m.group(2)), tailReturn(m.group(3)))
        ).toList :::
        arrowFn.findFirstMatchIn(line).map(m =>
          FunctionEntity(m.group(1), parseParams(m.group(2)), tailReturn(m.group(3)))
        ).toList :::
        modifierDecl.findFirstMatchIn(line).map(m =>
          FunctionEntity(m.group(2), parseParams(m.group(3)), cleanType(m.group(1)))
        ).toList :::
        defNoParens.findFirstMatchIn(line).map(m =>
          FunctionEntity(m.group(1), Nil, Option(m.group(2)).map(_.trim).filter(_.nonEmpty))
        ).toList :::
        rubyNoParens.findFirstMatchIn(line).map(m => FunctionEntity(m.group(1), Nil, None)).toList

    // Keep the first detection per name (the list above is in priority order).
    candidates
      .foldLeft((List.empty[FunctionEntity], Set.empty[String])) {
        case ((acc, seen), fn) if seen.contains(fn.name) => (acc, seen)
        case ((acc, seen), fn)                           => (acc :+ fn, seen + fn.name)
      }
      ._1

  /** Parses a raw parameter list (the text between the parentheses). */
  def parseParams(raw: String): List[ParameterEntity] =
    splitTopLevel(raw).map(_.trim).filter(_.nonEmpty).map(parseParam)

  private def parseParam(token: String): ParameterEntity =
    // Drop any default value, e.g. `x: Int = 3` or `count = 0`.
    val noDefault = token.split("=", 2).head.trim
    val colon = noDefault.indexOf(':')
    if colon >= 0 then
      val namePart = noDefault.substring(0, colon).trim
      val typePart = noDefault.substring(colon + 1).trim
      ParameterEntity(lastWord(namePart), typePart)
    else
      val parts = noDefault.split("\\s+").filter(_.nonEmpty)
      parts.length match
        case 0 => ParameterEntity("", "")
        case 1 => ParameterEntity(stripDecorations(parts(0)), "")
        // `Type name` (Java/C#): last token is the name, the rest is the type.
        case _ => ParameterEntity(stripDecorations(parts.last), parts.dropRight(1).mkString(" "))

  private def tailReturn(tail: String): Option[String] =
    arrowOrColonReturn
      .findFirstMatchIn(tail)
      .map(_.group(1).trim)
      .filter(_.nonEmpty)
      .orElse(goReturn.findFirstMatchIn(tail).map(_.group(1).trim).filter(_.nonEmpty))

  private def cleanType(raw: String): Option[String] =
    Option(raw.trim).filter(_.nonEmpty)

  private def lastWord(s: String): String =
    s.split("\\s+").filter(_.nonEmpty).lastOption.getOrElse(s)

  /** Strips leading decorations such as `...` (rest/variadic), `*`, `&`. */
  private def stripDecorations(s: String): String =
    s.dropWhile(c => !c.isLetter && c != '_')

  /** Splits on top-level commas only, ignoring commas nested inside brackets. */
  private def splitTopLevel(s: String): List[String] =
    val parts = ListBuffer.empty[String]
    val current = new StringBuilder
    var depth = 0
    for c <- s do
      c match
        case '(' | '[' | '{' | '<' => depth += 1; current += c
        case ')' | ']' | '}' | '>' => depth -= 1; current += c
        case ',' if depth == 0 => parts += current.toString; current.clear()
        case _ => current += c
    if current.nonEmpty then parts += current.toString
    parts.toList
