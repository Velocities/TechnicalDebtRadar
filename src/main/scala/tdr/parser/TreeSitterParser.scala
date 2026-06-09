package tdr.parser

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import org.treesitter.{TSNode, TSParser}
import tdr.types.{ClassEntity, FieldEntity, FunctionEntity, MethodEntity, ParameterEntity}

/** Structural source parsing backed by [[https://tree-sitter.github.io Tree-sitter]].
  *
  * This replaces the Version 1 line-based regex heuristics. Instead of guessing
  * declarations from a single line of text, we build a real concrete syntax tree
  * per file and read declarations off the AST: a type's body tells us exactly
  * which functions are its methods and which bindings are its fields, parameter
  * names/types come from the grammar's `name`/`type` fields (so e.g. Go's
  * `name Type` order is no longer ambiguous), and imports are read from the
  * grammar's import nodes rather than pattern-matched.
  *
  * One [[org.treesitter.TSParser]] is created per call; parsers are stateful and
  * must not be shared across the virtual threads that fan parsing out per file.
  */
object TreeSitterParser:

  /** Everything we extract from one source file. */
  final case class ParsedFile(
      imports: Set[String],
      classes: List[ClassEntity],
      functions: List[FunctionEntity]
  )

  val empty: ParsedFile = ParsedFile(Set.empty, Nil, Nil)

  /** True when we have a grammar for this extension (lower-cased, no dot). */
  def supports(ext: String): Boolean = TreeSitterLanguages.forExt(ext).isDefined

  /** Parses `source` as a file of type `ext`. Returns [[empty]] for unsupported
    * extensions. Tree-sitter is error-tolerant, so partial/invalid files still
    * yield whatever declarations could be recovered.
    */
  def parse(source: String, ext: String): ParsedFile =
    TreeSitterLanguages.forExt(ext) match
      case None => empty
      case Some(language) =>
        val parser = new TSParser()
        parser.setLanguage(language)
        val tree = parser.parseString(null, source)
        val ctx  = new Ctx(source.getBytes(StandardCharsets.UTF_8), ext.toLowerCase)
        val root = tree.getRootNode
        val imports = ctx.imports(root)
        ctx.fill(root) // populates classes + top-level functions
        ParsedFile(imports, ctx.classes, ctx.functions)

  // ----------------------------------------------------------------------------
  // Per-parse context: holds the source bytes (for node text) + the language.
  // ----------------------------------------------------------------------------
  private final class Ctx(bytes: Array[Byte], ext: String):

    private val allClasses = ListBuffer.empty[ClassAcc]
    private val topFns      = ListBuffer.empty[FunctionEntity]

    def classes: List[ClassEntity]      = allClasses.map(_.toEntity).toList
    def functions: List[FunctionEntity] = topFns.toList

    // -- low-level AST helpers ------------------------------------------------

    private def text(n: TSNode): String =
      if n == null || n.isNull then ""
      else new String(bytes.slice(n.getStartByte, n.getEndByte), StandardCharsets.UTF_8)

    private def namedChildren(n: TSNode): List[TSNode] =
      (0 until n.getNamedChildCount).map(n.getNamedChild).toList

    private def field(n: TSNode, name: String): Option[TSNode] =
      val c =
        try n.getChildByFieldName(name)
        catch case _: Throwable => null
      if c == null || c.isNull then None else Some(c)

    private def childOfType(n: TSNode, types: Set[String]): Option[TSNode] =
      namedChildren(n).find(c => types.contains(c.getType))

    private def childrenByField(n: TSNode, name: String): List[TSNode] =
      (0 until n.getChildCount).flatMap { i =>
        Option.when(Option(n.getFieldNameForChild(i)).contains(name))(n.getChild(i))
      }.toList

    private def stripQuotes(s: String): String =
      s.trim.replaceAll("^[\"'`]+", "").replaceAll("[\"'`]+$", "")

    private def cleanType(n: TSNode): String =
      if n.getType == "type_annotation" then
        // e.g. ": number" -> "number"
        text(n).dropWhile(c => c == ':' || c.isWhitespace).trim
      else text(n).trim

    // ========================================================================
    // Structure: classes (with methods + fields) and top-level functions
    // ========================================================================

    private final class ClassAcc(val name: String):
      val methods = ListBuffer.empty[MethodEntity]
      val fields  = ListBuffer.empty[FieldEntity]
      def toEntity: ClassEntity = ClassEntity(name, methods.toList, fields.toList)

    private val classTypes = Set(
      "class_definition", "class_declaration",          // scala/python, java/js/ts/kotlin/c#
      "object_definition", "trait_definition",          // scala
      "interface_declaration", "enum_declaration",      // java/ts/c#
      "struct_item", "trait_item", "enum_item", "union_item" // rust
    )

    private val funcTypes = Set(
      "function_definition", "function_declaration",    // scala/python, js/ts/go/kotlin
      "method_declaration", "method_definition",        // java/go/c#, js/ts
      "method_signature", "method_elem",                // ts interface, go interface
      "function_item", "method"                         // rust, ruby
    )

    private val fieldTriggers = Set(
      "field_declaration", "property_declaration",
      "val_definition", "var_definition", "variable_declaration",
      "expression_statement", "assignment", "call"
    )

    private val bodyTypes = Set(
      "class_body", "template_body", "declaration_list", "enum_class_body",
      "interface_body", "body_statement", "block", "field_declaration_list",
      "enum_body"
    )

    private def isClass(n: TSNode): Boolean =
      val t = n.getType
      if classTypes.contains(t) then true
      else if t == "class" || t == "module" then ext == "rb"
      else if t == "type_spec" then goTypeIsClass(n)
      else false

    /** A Go `type_spec` is a "class" only when it defines a struct or interface
      * (not a plain type alias such as `type Id int`).
      */
    private def goTypeIsClass(n: TSNode): Boolean =
      field(n, "type").exists(t => t.getType == "struct_type" || t.getType == "interface_type")

    private def className(n: TSNode): String =
      field(n, "name").map(text)
        .orElse(childOfType(n, Set("type_identifier", "constant")).map(text)) // kotlin/ruby
        .getOrElse("")

    /** The declarations that live directly in a type's body. */
    private def classMembers(n: TSNode): List[TSNode] =
      if n.getType == "type_spec" then // go: drill into struct/interface body
        field(n, "type").toList.flatMap { tp =>
          childOfType(tp, Set("field_declaration_list")) match
            case Some(list) => namedChildren(list)
            case None       => namedChildren(tp) // interface: method_elem children
        }
      else
        field(n, "body")
          .orElse(childOfType(n, bodyTypes))
          .map(namedChildren)
          .getOrElse(Nil)

    /** Walks the tree once, attributing every declaration to its enclosing type
      * (or to the file's top level). Method/function bodies are not descended
      * into, so locals are correctly excluded.
      */
    def fill(root: TSNode): Unit =
      def handle(node: TSNode, enclosing: Option[ClassAcc]): Unit =
        if isClass(node) then
          val acc = ClassAcc(className(node))
          allClasses += acc
          classMembers(node).foreach(m => handle(m, Some(acc)))
        else if funcTypes.contains(node.getType) then
          record(funcInfo(node), enclosing)
        else if isVarBoundFunction(node) then
          varBoundFunction(node).foreach(fn => record(fn, enclosing))
        else if enclosing.isDefined && fieldTriggers.contains(node.getType) then
          enclosing.get.fields ++= fieldsOf(node)
        else
          namedChildren(node).foreach(c => handle(c, enclosing))

      namedChildren(root).foreach(n => handle(n, None))

    private def record(fn: FunctionEntity, enclosing: Option[ClassAcc]): Unit =
      enclosing match
        case Some(c) => c.methods += MethodEntity(fn.name, fn.parameters, fn.returnDataType.getOrElse(""))
        case None    => topFns += fn

    // -- functions ------------------------------------------------------------

    private def funcInfo(n: TSNode): FunctionEntity =
      FunctionEntity(funcName(n), params(n), returnType(n))

    private def funcName(n: TSNode): String =
      field(n, "name").map(text)
        .orElse(childOfType(n, Set("simple_identifier", "identifier", "field_identifier", "property_identifier")).map(text))
        .getOrElse("")

    private def paramsNode(n: TSNode): Option[TSNode] =
      field(n, "parameters")
        .orElse(childOfType(n, Set("parameters", "formal_parameters", "parameter_list", "function_value_parameters")))

    private def params(n: TSNode): List[ParameterEntity] =
      paramsNode(n)
        .map(p => namedChildren(p).filterNot(_.getType == "comment").map(parseParam))
        .getOrElse(Nil)
        .filter(pe => pe.name.nonEmpty || pe.dataType.nonEmpty)

    private val bareParamTypes =
      Set("identifier", "simple_identifier", "shorthand_property_identifier_pattern")

    private def parseParam(p: TSNode): ParameterEntity =
      if bareParamTypes.contains(p.getType) then ParameterEntity(text(p), "")
      else
        val name = field(p, "name").map(text)
          .orElse(field(p, "pattern").map(text))
          .orElse(childOfType(p, Set("identifier", "simple_identifier")).map(text))
          .getOrElse("")
        val tpe = field(p, "type").map(cleanType)
          .orElse(childOfType(p, Set("user_type", "type_identifier", "predefined_type", "primitive_type", "type_annotation")).map(cleanType))
          .getOrElse("")
        ParameterEntity(name, tpe)

    private def returnType(n: TSNode): Option[String] =
      field(n, "return_type").map(cleanType)        // scala, rust, ts, python
        .orElse(field(n, "result").map(cleanType))  // go
        .orElse(field(n, "returns").map(cleanType)) // c#
        .orElse(field(n, "type").map(cleanType))    // java
        .orElse(
          if ext == "kt" || ext == "kts" then childOfType(n, Set("user_type")).map(text)
          else None
        )
        .map(_.trim)
        .filter(_.nonEmpty)

    /** JS/TS `const name = (...) => ...` / `const name = function (...) {}`. */
    private def isVarBoundFunction(n: TSNode): Boolean =
      n.getType == "variable_declarator" &&
        field(n, "value").exists(v => Set("arrow_function", "function", "function_expression").contains(v.getType))

    private def varBoundFunction(n: TSNode): Option[FunctionEntity] =
      for
        name  <- field(n, "name").map(text)
        value <- field(n, "value")
      yield FunctionEntity(name, params(value), returnType(value))

    // -- fields ---------------------------------------------------------------

    private def fieldsOf(n: TSNode): List[FieldEntity] =
      n.getType match
        case "val_definition" | "var_definition" => // scala
          field(n, "pattern").filter(_.getType == "identifier").map { p =>
            FieldEntity(text(p), field(n, "type").map(cleanType).getOrElse(""))
          }.toList
        case "field_declaration" =>                 // java / go / rust
          declaratorFields(n)
        case "variable_declaration" =>              // c# (wrapped in field_declaration)
          declaratorFields(n)
        case "property_declaration" =>              // kotlin
          childOfType(n, Set("variable_declaration")).toList.flatMap { vd =>
            val nm  = childOfType(vd, Set("simple_identifier")).map(text).getOrElse("")
            val tpe = childOfType(vd, Set("user_type")).map(text).getOrElse("")
            if nm.nonEmpty then List(FieldEntity(nm, tpe)) else Nil
          }
        case "expression_statement" =>              // python attribute assignment
          namedChildren(n).filter(_.getType == "assignment").flatMap(pyAssignField)
        case "assignment" =>
          pyAssignField(n)
        case "call" =>                              // ruby attr_accessor / reader / writer
          rubyAttrFields(n)
        case _ => Nil

    /** Handles both Java-style (`type` + `variable_declarator` on the node) and
      * C#-style (`variable_declaration` child holding the type + declarators).
      */
    private def declaratorFields(n: TSNode): List[FieldEntity] =
      val holder    = childOfType(n, Set("variable_declaration")).getOrElse(n)
      val declarators = namedChildren(holder).filter(_.getType == "variable_declarator")
      if declarators.nonEmpty then
        val tpe = field(holder, "type").map(cleanType).getOrElse("")
        declarators.flatMap(d => field(d, "name").map(text)).map(nm => FieldEntity(nm, tpe))
      else // go / rust: a single `name` + `type`
        field(n, "name").map(text).map(nm => FieldEntity(nm, field(n, "type").map(cleanType).getOrElse(""))).toList

    private def pyAssignField(assignment: TSNode): List[FieldEntity] =
      field(assignment, "left").filter(_.getType == "identifier").map { l =>
        FieldEntity(text(l), field(assignment, "type").map(cleanType).getOrElse(""))
      }.toList

    private val attrMethods = Set("attr_accessor", "attr_reader", "attr_writer")

    private def rubyAttrFields(call: TSNode): List[FieldEntity] =
      val method = field(call, "method").map(text).getOrElse("")
      if attrMethods.contains(method) then
        field(call, "arguments").toList
          .flatMap(namedChildren)
          .filter(_.getType == "simple_symbol")
          .map(s => FieldEntity(text(s).stripPrefix(":"), ""))
      else Nil

    // ========================================================================
    // Imports (dependency edges). Dispatch on language: import grammars differ.
    // ========================================================================

    def imports(root: TSNode): Set[String] =
      val out = mutable.LinkedHashSet.empty[String]
      def visit(n: TSNode): Unit =
        importsFrom(n).foreach(out += _)
        namedChildren(n).foreach(visit)
      visit(root)
      out.filter(_.nonEmpty).toSet

    private def importsFrom(n: TSNode): List[String] =
      ext match
        case "scala" | "sc" | "sbt"          => scalaImport(n)
        case "java"                          => javaImport(n)
        case "py" | "pyi"                    => pyImport(n)
        case "go"                            => goImport(n)
        case "rs"                            => rustImport(n)
        case "js" | "jsx" | "mjs" | "cjs" |
             "ts" | "tsx" | "mts" | "cts"    => jsImport(n)
        case "kt" | "kts"                    => ktImport(n)
        case "rb"                            => rubyImport(n)
        case "cs"                            => csImport(n)
        case _                               => Nil

    private def scalaImport(n: TSNode): List[String] =
      if n.getType == "import_declaration" then
        // `path`-tagged children include the `.` separator tokens; keep only the
        // identifier segments and re-join them into a dotted path.
        val path = childrenByField(n, "path").filter(_.getType == "identifier").map(text)
        if path.nonEmpty then List(path.mkString(".")) else List(text(n).stripPrefix("import").trim)
      else Nil

    private def javaImport(n: TSNode): List[String] =
      if n.getType == "import_declaration" then
        childOfType(n, Set("scoped_identifier", "identifier")).map(text).toList
      else Nil

    private def pyImport(n: TSNode): List[String] =
      n.getType match
        case "import_from_statement" => field(n, "module_name").map(text).toList
        case "import_statement"      => namedChildren(n).filter(_.getType == "dotted_name").map(text)
        case _                       => Nil

    private def goImport(n: TSNode): List[String] =
      if n.getType == "import_spec" then field(n, "path").map(p => stripQuotes(text(p))).toList
      else Nil

    private def rustImport(n: TSNode): List[String] =
      if n.getType == "use_declaration" then field(n, "argument").map(text).toList
      else Nil

    private def jsImport(n: TSNode): List[String] =
      n.getType match
        case "import_statement" => field(n, "source").map(s => stripQuotes(text(s))).toList
        case "call_expression"  =>
          val isRequire = field(n, "function").exists(f => text(f) == "require")
          if isRequire then
            field(n, "arguments").toList
              .flatMap(namedChildren)
              .filter(_.getType == "string")
              .map(s => stripQuotes(text(s)))
              .take(1)
          else Nil
        case _ => Nil

    private def ktImport(n: TSNode): List[String] =
      if n.getType == "import_header" then childOfType(n, Set("identifier")).map(text).toList
      else Nil

    private def rubyImport(n: TSNode): List[String] =
      if n.getType == "call" then
        val method = field(n, "method").map(text).getOrElse("")
        if method == "require" || method == "require_relative" then
          field(n, "arguments").toList
            .flatMap(namedChildren)
            .filter(_.getType == "string")
            .map(s => stripQuotes(text(s)))
            .take(1)
        else Nil
      else Nil

    private def csImport(n: TSNode): List[String] =
      if n.getType == "using_directive" then
        childOfType(n, Set("qualified_name", "identifier")).map(text).toList
      else Nil
