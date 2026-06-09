import org.scalatest.funsuite.AnyFunSuite

import tdr.ir.{ArchitectureGraph, FileNode}
import tdr.analysis.{CycleDetector, RiskCalculator}
import tdr.parser.{GraphBuilder, ImportParser}
import tdr.types.ScannedFile
import tdr.git.{FileHistory, GitHistory}

class AnalysisSpec extends AnyFunSuite:

  test("risk score increases with size, churn and coupling") {
    val small = FileNode("small.scala", loc = 10, churn = 1, contributors = 3)
    val big = FileNode("big.scala", loc = 1000, churn = 50, contributors = 3)
    assert(RiskCalculator.score(big, coupling = 10) > RiskCalculator.score(small, coupling = 1))
  }

  test("single-contributor files get a bus-factor penalty") {
    val solo = FileNode("solo.scala", loc = 100, churn = 10, contributors = 1)
    val shared = FileNode("shared.scala", loc = 100, churn = 10, contributors = 4)
    assert(RiskCalculator.score(solo, coupling = 2) > RiskCalculator.score(shared, coupling = 2))
  }

  test("rank returns files ordered by descending risk") {
    val graph = ArchitectureGraph(
      nodes = Map(
        "a.scala" -> FileNode("a.scala", 10, 1, 2),
        "b.scala" -> FileNode("b.scala", 500, 40, 1)
      ),
      edges = Map("a.scala" -> Set("b.scala"), "b.scala" -> Set.empty)
    )
    val ranked = RiskCalculator.rank(graph)
    assert(ranked.head.path == "b.scala")
  }

  test("cycle detector finds a two-node cycle") {
    val graph = ArchitectureGraph(
      nodes = Map(
        "a" -> FileNode("a", 1, 0, 0),
        "b" -> FileNode("b", 1, 0, 0)
      ),
      edges = Map("a" -> Set("b"), "b" -> Set("a"))
    )
    val cycles = CycleDetector.findCycles(graph)
    assert(cycles.size == 1)
    assert(cycles.head.toSet == Set("a", "b"))
  }

  test("cycle detector reports none for an acyclic graph") {
    val graph = ArchitectureGraph(
      nodes = Map(
        "a" -> FileNode("a", 1, 0, 0),
        "b" -> FileNode("b", 1, 0, 0)
      ),
      edges = Map("a" -> Set("b"), "b" -> Set.empty)
    )
    assert(CycleDetector.findCycles(graph).isEmpty)
  }

  test("import parser extracts targets across languages") {
    assert(ImportParser.importsIn("import scala.collection.mutable").contains("scala.collection.mutable"))
    assert(ImportParser.importsIn("from os.path import join").contains("os.path"))
    assert(ImportParser.importsIn("""import { x } from './util'""").contains("./util"))
    assert(ImportParser.importsIn("const fs = require('fs')").contains("fs"))
  }

  test("graph assembly resolves imports and merges git metrics") {
    val scanned = List(
      ScannedFile("src/A.scala", loc = 20, imports = Set("pkg.B")),
      ScannedFile("src/pkg/B.scala", loc = 10, imports = Set.empty)
    )
    val history = Map(
      "src/A.scala" -> FileHistory("src/A.scala", churn = 3, contributors = Set("Alice", "Bob"))
    )
    val graph = GraphBuilder.assemble(scanned, history)

    assert(graph.nodes("src/A.scala").churn == 3)
    assert(graph.nodes("src/A.scala").contributors == 2)
    assert(graph.nodes("src/pkg/B.scala").churn == 0)
    assert(graph.dependencies("src/A.scala") == Set("src/pkg/B.scala"))
  }

  test("git log parsing aggregates churn and contributors") {
    val log =
      "\u0001Alice\n" +
        "src/A.scala\n" +
        "src/B.scala\n" +
        "\u0001Bob\n" +
        "src/A.scala\n"
    val parsed = GitHistory.parse(log)
    assert(parsed("src/A.scala").churn == 2)
    assert(parsed("src/A.scala").contributors == Set("Alice", "Bob"))
    assert(parsed("src/B.scala").churn == 1)
  }
