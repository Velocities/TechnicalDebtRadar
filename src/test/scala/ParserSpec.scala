import org.scalatest.funsuite.AnyFunSuite

import tdr.parser.TreeSitterParser
import tdr.types.ParameterEntity

/** Tree-sitter parsing: imports, type declarations, and function/parameter
  * extraction across the supported languages. Unlike the old line-based
  * heuristics these are whole-source parses, so each snippet is given enough
  * context to form a valid (or near-valid) tree.
  */
class ParserSpec extends AnyFunSuite:

  test("import parser extracts targets across languages") {
    assert(TreeSitterParser.parse("import scala.collection.mutable", "scala").imports.contains("scala.collection.mutable"))
    assert(TreeSitterParser.parse("import java.util.List;", "java").imports.contains("java.util.List"))
    assert(TreeSitterParser.parse("from os.path import join", "py").imports.contains("os.path"))
    assert(TreeSitterParser.parse("import sys", "py").imports.contains("sys"))
    assert(TreeSitterParser.parse("import { x } from './util'", "ts").imports.contains("./util"))
    assert(TreeSitterParser.parse("const fs = require('fs')", "js").imports.contains("fs"))
    assert(TreeSitterParser.parse("use std::collections::HashMap;", "rs").imports.contains("std::collections::HashMap"))
    assert(TreeSitterParser.parse("package main\nimport \"fmt\"", "go").imports.contains("fmt"))
    assert(TreeSitterParser.parse("require 'set'", "rb").imports.contains("set"))
    assert(TreeSitterParser.parse("using System;", "cs").imports.contains("System"))
  }

  test("class parser detects type declarations across languages") {
    assert(TreeSitterParser.parse("class Foo", "scala").classes.map(_.name) == List("Foo"))
    assert(TreeSitterParser.parse("class Foo:\n    pass", "py").classes.map(_.name) == List("Foo"))
    assert(TreeSitterParser.parse("public final class Bar {}", "java").classes.map(_.name) == List("Bar"))
    assert(TreeSitterParser.parse("enum class Color { RED }", "kt").classes.map(_.name) == List("Color"))
    assert(TreeSitterParser.parse("package m\ntype Server struct {}", "go").classes.map(_.name) == List("Server"))
    assert(TreeSitterParser.parse("trait Greeter", "scala").classes.map(_.name) == List("Greeter"))
    assert(TreeSitterParser.parse("interface Repo {}", "ts").classes.map(_.name) == List("Repo"))
    assert(TreeSitterParser.parse("x = 1", "py").classes.isEmpty)
  }

  test("function parser handles Scala-style typed params and return") {
    val fn = TreeSitterParser.parse("def add(a: Int, b: Int): Int = a + b", "scala").functions.head
    assert(fn.name == "add")
    assert(fn.parameters == List(ParameterEntity("a", "Int"), ParameterEntity("b", "Int")))
    assert(fn.returnDataType.contains("Int"))
  }

  test("function parser handles Rust arrow return") {
    val fn = TreeSitterParser.parse("fn compute(x: i32) -> i32 { x }", "rs").functions.head
    assert(fn.name == "compute")
    assert(fn.parameters == List(ParameterEntity("x", "i32")))
    assert(fn.returnDataType.contains("i32"))
  }

  test("function parser handles untyped Python params") {
    val fn = TreeSitterParser.parse("def greet(name):\n    return name", "py").functions.head
    assert(fn.name == "greet")
    assert(fn.parameters == List(ParameterEntity("name", "")))
    assert(fn.returnDataType.isEmpty)
  }

  test("function parser handles Java modifier + type-first params") {
    val cls = TreeSitterParser.parse(
      "class X { public boolean isValid(String input) { return true; } }", "java"
    ).classes.head
    val m = cls.methods.head
    assert(m.name == "isValid")
    assert(m.parameters == List(ParameterEntity("input", "String")))
    assert(m.returnDataType == "boolean")
  }

  test("function parser handles Go return type and param order") {
    val fn = TreeSitterParser.parse("package m\nfunc handle(count int) error { return nil }", "go").functions.head
    assert(fn.name == "handle")
    assert(fn.parameters == List(ParameterEntity("count", "int")))
    assert(fn.returnDataType.contains("error"))
  }

  test("function parser handles JS/TS arrow functions") {
    val fn = TreeSitterParser.parse("const sum = (a, b) => a + b", "js").functions.head
    assert(fn.name == "sum")
    assert(fn.parameters == List(ParameterEntity("a", ""), ParameterEntity("b", "")))
  }

  test("function parser ignores non-declarations") {
    assert(TreeSitterParser.parse("val y = compute(x)", "scala").functions.isEmpty)
    assert(TreeSitterParser.parse("if (ready) { go() }", "java").functions.isEmpty)
  }
