# Technical Debt Radar — Project Guide

This document explains how the project is laid out, how to build and run it, and
how to produce a standalone binary. For the product vision and goals, see
[`README.md`](./README.md).

---

## Overview

Technical Debt Radar analyzes a local Git repository and reports architectural
risk: which files are the biggest hotspots and where circular dependencies hide.

It implements the Version 1 pipeline from the README:

```
Repository → Scanner / Parser → Architecture IR → Analysis Engine → Report
```

The **Architecture IR** (`ArchitectureGraph`) is a language-independent graph of
files (nodes) and dependencies (edges). Every analysis runs on the IR, so the
engine never needs to know which language produced the data.

---

## Requirements

**To build / develop:**

| Tool  | Version used | Notes                                              |
| ----- | ------------ | -------------------------------------------------- |
| JDK   | **21+**      | required for virtual threads (JEP 444) + jpackage  |
| sbt   | 1.12.x       | pinned in `project/build.properties`               |
| Scala | 3.8.3        | resolved automatically by sbt                      |
| Git   | any recent   | invoked at runtime for churn/authors               |

> The build sets a `-release:21` floor. Make sure sbt runs on JDK 21+ — set
> `JAVA_HOME` to a JDK 21 install (e.g. `winget install Microsoft.OpenJDK.21`),
> or pass `sbt -java-home "<jdk21>"`.

**To run the standalone binary:** nothing — the bundled launcher ships its own
Java runtime. The only runtime dependency is **`git`** on your `PATH` (used to
read commit history of the repo being analyzed).

---

## Project Structure

```
TechnicalDebtRadar/
├─ README.md                 Product vision and goals
├─ PROJECT_GUIDE.md          This file
├─ .gitignore
├─ build.sbt                 Build definition + packaging config
├─ project/
│  ├─ build.properties       Pinned sbt version
│  └─ plugins.sbt            sbt-assembly (fat JAR) plugin
├─ scripts/
│  ├─ build-binary.ps1       Standalone binary build (Windows)
│  └─ build-binary.sh        Standalone binary build (macOS/Linux)
└─ src/
   ├─ main/scala/
   │  ├─ Main.scala          CLI entry point (`@main def radar`)
   │  └─ tdr/
   │     ├─ ir/
   │     │  └─ ArchitectureGraph.scala   Language-independent IR
   │     ├─ git/
   │     │  └─ GitHistory.scala          Churn + contributors via `git log`
   │     ├─ concurrent/
   │     │  └─ Concurrency.scala         Virtual-thread executor / ExecutionContext
│     ├─ parser/
│     │  ├─ RepositoryScanner.scala   Walks files, counts LOC
│     │  ├─ TreeSitterLanguages.scala Maps file extension -> tree-sitter grammar
│     │  ├─ TreeSitterParser.scala    Tree-sitter parse: imports, classes, functions
│     │  └─ GraphBuilder.scala        Fuses scan + git + imports into the IR (parallel)
   │     ├─ analysis/
   │     │  ├─ RiskCalculator.scala      Risk scoring + ranking
   │     │  └─ CycleDetector.scala       Circular dependencies (Tarjan SCC)
   │     └─ report/
   │        └─ Report.scala              Text report rendering
   └─ test/scala/
      └─ AnalysisSpec.scala  Unit tests for the analysis/parsing logic
```

### How the pieces fit together

1. `RepositoryScanner` walks the repo, skipping `target/`, `node_modules/`,
   `.git/`, etc. For each source file it counts lines of code and runs
   `TreeSitterParser`, which builds a real concrete syntax tree (via the
   tree-sitter grammar for that language) to extract imports, classes (with
   their methods and fields) and top-level functions.
2. `GitHistory` runs `git log` and aggregates per-file **churn** (commit count)
   and **contributor** set.
3. `GraphBuilder` resolves import strings to file paths and merges everything
   into an `ArchitectureGraph`.
4. `RiskCalculator` and `CycleDetector` analyze the graph.
5. `Report` renders the hotspot table and the list of circular dependencies.

---

## Concurrency & Performance

On large repositories the cost is dominated by **blocking** work: reading and
parsing thousands of files, plus waiting on the `git log` subprocess. The
pipeline exploits this:

- **Git history and file scanning run concurrently.** They are independent, so
  `GraphBuilder.buildAsync` launches both as `Future`s up front and joins them.
- **Per-file reads/parsing are fanned out** with `Future.traverse`, and edge
  resolution (roughly O(files²)) is parallelized the same way.

These tasks run on **virtual threads** (JDK 21+, JEP 444) via an
`ExecutionContext` backed by `Executors.newVirtualThreadPerTaskExecutor()` (see
`tdr/concurrent/Concurrency.scala`). Virtual threads are the right tool here for
two reasons:

1. **Blocking I/O scales cheaply.** A virtual thread that blocks on a file read
   or the git subprocess is *unmounted* from its carrier OS thread, so we can
   have thousands of in-flight blocking tasks without exhausting OS threads.
2. **No thread-pool starvation/deadlock.** The classic hazard — tasks on a
   bounded pool blocking while waiting on other tasks queued in the *same*
   pool — does not apply, because each task gets its own virtual thread instead
   of competing for a small set of carrier threads. The single blocking
   `Await` lives on the *calling* thread (`Concurrency.withVirtualThreads`),
   never on a pool thread.

`CycleDetector` (Tarjan SCC) is left sequential — it is inherently
order-dependent and cheap relative to scanning.

---

## Running From Source

```bash
# Run the analyzer against a repository (defaults to ".")
sbt "run /path/to/some/repo"

# Run against the current project
sbt "run ."

# Run the test suite
sbt test
```

### Reading the report

The hotspot table is sorted by descending risk:

| Column  | Meaning                                            |
| ------- | -------------------------------------------------- |
| `risk`  | Composite risk score (higher = more dangerous)     |
| `loc`   | Non-blank lines of code                            |
| `churn` | Number of commits that touched the file            |
| `auth`  | Distinct contributors (low count = bus-factor risk)|
| `coupl` | Coupling: files it imports + files that import it  |

> Note: `churn` and `auth` are `0` for files not yet committed to Git — commit
> your files first to populate history-based metrics.

---

## Building a Standalone Binary

The goal: a launcher developers can run **without installing Scala or Java**.
We do this in two stages:

1. **`sbt-assembly`** packs the app and all libraries into one fat JAR.
2. **`jpackage`** (bundled with the JDK) wraps that JAR together with a trimmed
   Java runtime into a native launcher.

### One-shot build

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File scripts/build-binary.ps1
```

```bash
# macOS / Linux
./scripts/build-binary.sh
```

### What you get

| Platform     | Launcher path                  |
| ------------ | ------------------------------ |
| Windows      | `dist\radar\radar.exe`         |
| macOS/Linux  | `dist/radar/bin/radar`         |

Run it like any executable:

```powershell
.\dist\radar\radar.exe C:\path\to\some\repo
```

```bash
./dist/radar/bin/radar /path/to/some/repo
```

The `dist/radar/` folder is fully self-contained (~140 MB, mostly the embedded
runtime). Zip it up and hand it to a teammate — they need only `git` installed.

### Doing it manually

If you'd rather not use the scripts:

```bash
sbt assembly                       # → target/scala-3.8.3/technical-debt-radar.jar
mkdir stage && cp target/scala-3.8.3/technical-debt-radar.jar stage/
jpackage --type app-image --name radar \
  --input stage --main-jar technical-debt-radar.jar \
  --main-class radar --win-console --dest dist
```

(We stage the JAR in its own folder because `jpackage --input` bundles every
file in that directory.)

> **Windows:** the `--win-console` flag is required. Without it `jpackage`
> builds a GUI-subsystem launcher that detaches from the terminal, so the tool
> runs but you never see its `stdout`. On macOS/Linux the flag is ignored.

### Just want a runnable JAR?

If a JDK/JRE is acceptable on the target machine (Java, not Scala), the fat JAR
alone is enough:

```bash
sbt assembly
java -jar target/scala-3.8.3/technical-debt-radar.jar /path/to/repo
```

---

## Current Limitations & Next Steps

This is an intentionally lightweight Version 1 scaffold. Known rough edges:

- **Import resolution** (`GraphBuilder.resolve`) is a substring heuristic.
  Import *targets* are now extracted precisely by tree-sitter, but mapping a
  target back to a file on disk still relies on path-suffix matching.
- **Risk weighting** (`RiskCalculator.score`) is a reasonable starting formula,
  not calibrated against real incident data.
- The bundled runtime is the full JDK; it can be slimmed with `jlink`/`jdeps`
  to shrink the binary.
- Reports are text-only — graph visualization and HTML/JSON output are open.
