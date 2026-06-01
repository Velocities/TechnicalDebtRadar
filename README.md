# Technical Debt Radar

## Problem

Software systems naturally become harder to maintain as they grow.

Teams often discover architectural problems only after they become expensive:

* Classes become too large.
* Dependencies become tightly coupled.
* Critical modules become change hotspots.
* Circular dependencies appear.
* Ownership becomes unclear.
* Architecture drifts from the original design.

Most teams lack visibility into these risks until they cause bugs, outages, or slow development.

## Solution

Technical Debt Radar analyzes source code repositories and Git history to identify architectural risks before they become serious problems.

Rather than focusing on language-specific implementation details, the system converts codebases into a language-independent architecture graph.

This graph is then used to:

* Detect highly coupled components.
* Identify change hotspots.
* Track architecture drift.
* Calculate technical debt risk scores.
* Visualize system structure.
* Generate reports for engineers and managers.

## Core Architecture

Repository
→ Language Parser
→ Architecture Intermediate Representation (IR)
→ Analysis Engine
→ Reports and Visualizations

The IR acts as a common representation shared across all supported languages.

## Version 1 Goals

Input:

* Local Git repository

Analysis:

* File dependency graph
* Import relationships
* File size
* Git churn
* Contributor count

Output:

* Risk scores
* Hotspot report
* Dependency graph
* Circular dependency detection

## Future Goals

* Tree-sitter support
* Class-level analysis
* Service-level analysis
* Architecture drift tracking
* Historical trend analysis
* Pull request risk analysis
* Team ownership analysis
* AI-assisted explanations
* Self-hosted enterprise deployments

## Success Criteria

A developer can point the tool at a repository and receive actionable information within one minute.

The tool should answer:

* What files are most dangerous?
* What modules are growing unstable?
* Where is coupling increasing?
* What should engineers refactor first?
