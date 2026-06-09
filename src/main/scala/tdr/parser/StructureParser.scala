package tdr.parser

import scala.collection.mutable.ListBuffer
import tdr.types.{ClassEntity, FieldEntity, FunctionEntity, MethodEntity}

/** Turns a whole file into its structure: the classes it declares (each with the
  * methods and fields nested inside it) and the top-level functions.
  *
  * The line-based [[ClassParser]] / [[FunctionParser]] / [[FieldParser]] only see
  * one line at a time, so they cannot tell which class a method belongs to. This
  * parser adds the missing nesting context by tracking block scope as it walks
  * the file:
  *   - brace languages (Scala/Java/Kotlin/JS/TS/Go/Rust/C#) -> count `{` / `}`
  *   - Python -> indentation
  *   - Ruby -> block keywords vs `end`
  *
  * A declaration is a *method* when it sits directly inside a class body, a
  * *field* under the same condition, and a top-level *function* otherwise. This
  * is heuristic (Version 1); precise parsing is the Tree-sitter follow-up.
  */
object StructureParser:

  /** Classes (with their methods/fields) plus the file's top-level functions. */
  final case class FileStructure(classes: List[ClassEntity], functions: List[FunctionEntity])

  private enum Style:
    case Braces, Indent, End

  private def styleFor(ext: String): Style = ext match
    case "py" => Style.Indent
    case "rb" => Style.End
    case _    => Style.Braces

  def parse(lines: List[String], ext: String): FileStructure =
    styleFor(ext) match
      case Style.Braces => parseDepth(lines, ext, bracesDelta)
      case Style.End    => parseDepth(lines, ext, rubyDelta)
      case Style.Indent => parseIndent(lines, ext)

  /** Mutable accumulator for a class while we are inside its body. */
  private final class ClassAcc(val name: String, val openLevel: Int):
    val methods = ListBuffer.empty[MethodEntity]
    val fields = ListBuffer.empty[FieldEntity]
    def toEntity: ClassEntity = ClassEntity(name, methods.toList, fields.toList)

  private def asMethod(f: FunctionEntity): MethodEntity =
    MethodEntity(f.name, f.parameters, f.returnDataType.getOrElse(""))

  // ============================================================
  // Depth-based walk (brace languages and Ruby)
  // ============================================================
  private def parseDepth(
      lines: List[String],
      ext: String,
      delta: String => (Int, Int)
  ): FileStructure =
    var depth = 0
    val openClasses = ListBuffer.empty[ClassAcc] // classes whose body we are inside
    val allClasses = ListBuffer.empty[ClassAcc]  // every class, in declaration order
    val topFns = ListBuffer.empty[FunctionEntity]

    for raw <- lines do
      val line = stripNoise(raw)
      val (opens, closes) = delta(raw)
      val clsName = ClassParser.classesIn(line).headOption.map(_.name)
      val fns = FunctionParser.functionsIn(line)
      // We are directly in a class body when the innermost open class opened at
      // exactly the current depth (a method body would have raised the depth).
      val container = openClasses.lastOption.filter(_.openLevel == depth)

      if clsName.isDefined then
        val acc = ClassAcc(clsName.get, depth + opens)
        allClasses += acc
        openClasses += acc
      else if fns.nonEmpty then
        for f <- fns do
          container match
            case Some(c)                                  => c.methods += asMethod(f)
            case None if depth == 0 && openClasses.isEmpty => topFns += f
            case None                                      => () // nested/local function
      else
        container.foreach(c => c.fields ++= FieldParser.fieldsIn(line, ext))

      depth += opens - closes
      while openClasses.nonEmpty && depth < openClasses.last.openLevel do
        openClasses.remove(openClasses.length - 1)

    FileStructure(allClasses.map(_.toEntity).toList, topFns.toList)

  // ============================================================
  // Indentation-based walk (Python)
  // ============================================================
  private final case class IndentScope(classAcc: Option[ClassAcc], indent: Int)

  private def parseIndent(lines: List[String], ext: String): FileStructure =
    val stack = ListBuffer.empty[IndentScope]
    val allClasses = ListBuffer.empty[ClassAcc]
    val topFns = ListBuffer.empty[FunctionEntity]

    for raw <- lines if raw.trim.nonEmpty do
      val line = stripNoise(raw)
      val indent = raw.takeWhile(c => c == ' ' || c == '\t').length
      // Leaving any scope whose body is no longer indented under it.
      while stack.nonEmpty && indent <= stack.last.indent do
        stack.remove(stack.length - 1)

      val enclosingClass = stack.lastOption.flatMap(_.classAcc)
      val clsName = ClassParser.classesIn(line).headOption.map(_.name)
      val fns = FunctionParser.functionsIn(line)

      if clsName.isDefined then
        val acc = ClassAcc(clsName.get, indent)
        allClasses += acc
        stack += IndentScope(Some(acc), indent)
      else if fns.nonEmpty then
        for f <- fns do
          enclosingClass match
            case Some(c)                 => c.methods += asMethod(f)
            case None if stack.isEmpty   => topFns += f
            case None                    => () // function nested inside another function
        stack += IndentScope(None, indent) // its body is not the class body
      else
        enclosingClass.foreach(c => c.fields ++= FieldParser.fieldsIn(line, ext))

    FileStructure(allClasses.map(_.toEntity).toList, topFns.toList)

  // ============================================================
  // Per-line "block opened / block closed" counts
  // ============================================================
  private def bracesDelta(line: String): (Int, Int) =
    val s = stripNoise(line)
    (s.count(_ == '{'), s.count(_ == '}'))

  private val rubyOpener = """^\s*(?:class|module|def|if|unless|while|until|for|case|begin)\b""".r
  private val rubyDoBlock = """\bdo\b(\s*\|[^|]*\|)?\s*$""".r
  private val rubyEnd = """\bend\b""".r

  private def rubyDelta(line: String): (Int, Int) =
    val s = stripNoise(line)
    var opens = 0
    if rubyOpener.findFirstMatchIn(s).isDefined then opens += 1
    if rubyDoBlock.findFirstMatchIn(s).isDefined then opens += 1
    (opens, rubyEnd.findAllMatchIn(s).size)

  /** Blanks out string/char literal contents and strips `//` and `#` line
    * comments so braces / keywords inside them are not miscounted. Heuristic
    * (does not fully track escape sequences).
    */
  private def stripNoise(line: String): String =
    val sb = new StringBuilder
    var i = 0
    var inSingle = false
    var inDouble = false
    var done = false
    while i < line.length && !done do
      val c = line.charAt(i)
      if !inSingle && !inDouble && c == '/' && i + 1 < line.length && line.charAt(i + 1) == '/' then
        done = true
      else if !inSingle && !inDouble && c == '#' then
        done = true
      else
        if c == '"' && !inSingle then { inDouble = !inDouble; sb += ' ' }
        else if c == '\'' && !inDouble then { inSingle = !inSingle; sb += ' ' }
        else if inSingle || inDouble then sb += ' '
        else sb += c
        i += 1
    sb.toString
