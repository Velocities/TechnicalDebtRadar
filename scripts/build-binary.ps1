# Builds a standalone Technical Debt Radar binary (Windows / PowerShell).
#
# Steps:
#   1. Assemble a single fat JAR with sbt.
#   2. Stage the JAR in a clean directory (jpackage bundles everything in --input).
#   3. Run jpackage to produce a native launcher with an embedded Java runtime.
#
# Output: dist\radar\radar.exe  (no Scala or Java required to run it)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "==> Building fat JAR (sbt assembly)..."
sbt -batch "assembly"

Write-Host "==> Staging JAR..."
Remove-Item -Recurse -Force stage, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force stage | Out-Null
Copy-Item "target\scala-3.8.3\technical-debt-radar.jar" "stage\"

Write-Host "==> Packaging standalone binary (jpackage)..."
jpackage `
  --type app-image `
  --name radar `
  --input stage `
  --main-jar technical-debt-radar.jar `
  --main-class radar `
  --dest dist

Write-Host ""
Write-Host "Done. Standalone binary: dist\radar\radar.exe"
Write-Host "Try it:  .\dist\radar\radar.exe C:\path\to\some\repo"
