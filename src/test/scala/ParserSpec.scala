import org.scalatest.funsuite.AnyFunSuite

import tdr.parser.{ClassParser, FunctionParser, ImportParser}
import tdr.types.ParameterEntity

class ParserSpec extends AnyFunSuite:

  test("import parser extracts targets across languages") {
    assert(ImportParser.importsIn("import scala.collection.mutable").contains("scala.collection.mutable"))
    assert(ImportParser.importsIn("from os.path import join").contains("os.path"))
    assert(ImportParser.importsIn("""import { x } from './util'""").contains("./util"))
    assert(ImportParser.importsIn("const fs = require('fs')").contains("fs"))
  }

  test("class parser detects type declarations across languages") {
    assert(ClassParser.classesIn("class Foo:").map(_.name) == List("Foo"))               // Scala / Python
    assert(ClassParser.classesIn("public final class Bar {").map(_.name) == List("Bar")) // Java
    assert(ClassParser.classesIn("enum class Color {").map(_.name) == List("Color"))      // Kotlin
    assert(ClassParser.classesIn("type Server struct {").map(_.name) == List("Server"))   // Go
    assert(ClassParser.classesIn("trait Greeter {").map(_.name) == List("Greeter"))       // Scala / Rust
    assert(ClassParser.classesIn("interface Repo {").map(_.name) == List("Repo"))         // Java / TS
    assert(ClassParser.classesIn("x = 1").isEmpty)
  }

  test("function parser handles Scala-style typed params and return") {
    val fn = FunctionParser.functionsIn("def add(a: Int, b: Int): Int =").head
    assert(fn.name == "add")
    assert(fn.parameters == List(ParameterEntity("a", "Int"), ParameterEntity("b", "Int")))
    assert(fn.returnDataType.contains("Int"))
  }

  test("function parser handles Rust arrow return") {
    val fn = FunctionParser.functionsIn("fn compute(x: i32) -> i32 {").head
    assert(fn.name == "compute")
    assert(fn.parameters == List(ParameterEntity("x", "i32")))
    assert(fn.returnDataType.contains("i32"))
  }

  test("function parser handles untyped Python params") {
    val fn = FunctionParser.functionsIn("def greet(name):").head
    assert(fn.name == "greet")
    assert(fn.parameters == List(ParameterEntity("name", "")))
    assert(fn.returnDataType.isEmpty)
  }

  test("function parser handles Java modifier + type-first params") {
    val fn = FunctionParser.functionsIn("public boolean isValid(String input) {").head
    assert(fn.name == "isValid")
    assert(fn.parameters == List(ParameterEntity("input", "String")))
    assert(fn.returnDataType.contains("boolean"))
  }

  test("function parser handles Go return type") {
    val fn = FunctionParser.functionsIn("func handle(count int) error {").head
    assert(fn.name == "handle")
    assert(fn.returnDataType.contains("error"))
  }

  test("function parser handles JS/TS arrow functions") {
    val fn = FunctionParser.functionsIn("const sum = (a, b) => a + b").head
    assert(fn.name == "sum")
    assert(fn.parameters == List(ParameterEntity("a", ""), ParameterEntity("b", "")))
  }

  test("function parser ignores non-declarations") {
    assert(FunctionParser.functionsIn("return compute(x)").isEmpty)
    assert(FunctionParser.functionsIn("if (ready) {").isEmpty)
  }
