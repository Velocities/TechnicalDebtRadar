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

## Version 1.0 - Repository Analysis

Goal:
Analyze a local repository and identify architectural characteristics.

Input:

* Local Git repository

Features:

* File dependency graph
* Class dependency graph
* Import relationships
* Dependency fan-in and fan-out
* File size
* Method count
* Field count
* Class count
* Function count
* Git churn
* Contributor count
* Circular dependency detection
* Dependency hotspots
* Risk indicators
* Tree-sitter support
* Service-level analysis
* Console report

Output:
Current repository architecture snapshot — risk scores, hotspot report, and dependency graph.

---

## Version 1.5 - Trend Analysis

Goal:
Understand how architecture evolves over time.

Features:

* Dependency growth tracking
* Method growth tracking
* Field growth tracking
* Churn trend reporting
* Stability metrics
* Architecture drift tracking
* Historical comparison reports

Output:
Architectural trend report.

---

## Version 2.0 - Standards & Governance

Goal:
Allow teams to define engineering standards.

Features:

* Custom architecture budgets
* Documentation coverage targets
* Dependency limits
* Method count limits
* Constructor parameter limits
* Team ownership analysis
* Compliance reporting

Output:
Standards compliance report.

---

## Version 3.0 - Pull Request Analysis

Goal:
Analyze architectural impact of code changes.

Features:

* Git diff analysis
* Pull request architecture delta
* Dependency change detection
* Complexity change detection
* Documentation change detection
* Risk increase/decrease reporting

Output:
Architectural impact report for pull requests.

---

## Version 3.5 - GitHub Actions (Beta)

Goal:
Integrate architecture analysis into CI/CD pipelines.

Features:

* GitHub Actions support
* Pull request comments
* Automated reports
* Trend tracking across builds
* Self-hosted enterprise deployments

Output:
Continuous architecture monitoring.

---

## Version 4.0 - AI Assistance

Goal:
Provide architecture guidance.

Features:

* Risk explanations
* Refactoring suggestions
* Architecture summaries
* Design recommendations

Output:
AI-generated architecture guidance.

---

Core Principle:

Technical Debt Radar does not attempt to determine whether code is objectively good or bad.

Instead, it measures architectural characteristics, highlights trends, and surfaces potential risks for engineering teams to evaluate.

## Success Criteria

A developer can point the tool at a repository and receive actionable information within one minute.

The tool should answer:

* What files are most dangerous?
* What modules are growing unstable?
* Where is coupling increasing?
* What should engineers refactor first?
