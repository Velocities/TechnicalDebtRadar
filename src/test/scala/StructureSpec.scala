import org.scalatest.funsuite.AnyFunSuite

import tdr.parser.TreeSitterParser

/** Nesting-aware parsing: tree-sitter attributes each method/field to the type
  * whose body it lives in, and leaves method-body locals and top-level
  * functions out of any class.
  */
class StructureSpec extends AnyFunSuite:

  test("scala: methods and fields attach to their class, locals excluded") {
    val code =
      """|class Account(val id: Int) {
         |  val balance: Int = 0
         |  def deposit(amount: Int): Int = balance + amount
         |  def withdraw(amount: Int): Int = {
         |    val local = balance - amount
         |    local
         |  }
         |}
         |""".stripMargin
    val cls = TreeSitterParser.parse(code, "scala").classes.head
    assert(cls.name == "Account")
    assert(cls.methods.map(_.name) == List("deposit", "withdraw"))
    assert(cls.fields.map(_.name) == List("balance")) // `local` is a method-body local
  }

  test("java: modifier fields and methods are captured") {
    val code =
      """|public class Counter {
         |  private int count = 0;
         |  public void increment() {
         |    count = count + 1;
         |  }
         |  public int get() { return count; }
         |}
         |""".stripMargin
    val cls = TreeSitterParser.parse(code, "java").classes.head
    assert(cls.fields.map(f => (f.name, f.dataType)) == List(("count", "int")))
    assert(cls.methods.map(_.name) == List("increment", "get"))
  }

  test("python: indentation determines class membership") {
    val code =
      """|class Dog:
         |    species = "canine"
         |    def __init__(self, name):
         |        self.name = name
         |    def bark(self):
         |        return "woof"
         |""".stripMargin
    val cls = TreeSitterParser.parse(code, "py").classes.head
    assert(cls.name == "Dog")
    assert(cls.fields.map(_.name) == List("species")) // self.name is a method-body local
    assert(cls.methods.map(_.name) == List("__init__", "bark"))
  }

  test("go: struct fields captured; receiver methods are top-level functions") {
    val code =
      """|package main
         |type Account struct {
         |  ID int
         |  Balance float64
         |}
         |func (a Account) Deposit(amount float64) float64 {
         |  return a.Balance + amount
         |}
         |""".stripMargin
    val structure = TreeSitterParser.parse(code, "go")
    val cls = structure.classes.head
    assert(cls.name == "Account")
    assert(cls.fields.map(_.name) == List("ID", "Balance"))
    assert(structure.functions.map(_.name) == List("Deposit"))
  }

  test("top-level functions are not attributed to any class") {
    val code =
      """|def standalone(x: Int): Int = x + 1
         |class Holder {
         |  def method(): Int = 1
         |}
         |""".stripMargin
    val structure = TreeSitterParser.parse(code, "scala")
    assert(structure.functions.map(_.name) == List("standalone"))
    assert(structure.classes.head.methods.map(_.name) == List("method"))
  }
