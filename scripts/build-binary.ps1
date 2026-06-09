# Builds a standalone Technical Debt Radar binary (Windows / PowerShell).
#
# Requires JDK 21+ (virtual threads + jpackage). The script resolves a suitable
# JDK from JAVA_HOME, then falls back to C:\Program Files\Microsoft\jdk-21*.
#
# Steps:
#   1. Assemble a single fat JAR with sbt (built on JDK 21).
#   2. Stage the JAR in a clean directory (jpackage bundles everything in --input).
#   3. Run jpackage to produce a native launcher with an embedded JDK 21 runtime.
#
# Output: dist\radar\radar.exe  (no Scala or Java required to run it)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Get-JavaMajor($javaExe) {
  # `java -version` prints to stderr; capture it without tripping -ErrorAction Stop.
  $old = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { $out = (& $javaExe -version 2>&1 | Out-String) }
  finally { $ErrorActionPreference = $old }
  if ($out -match '"(\d+)') { return [int]$Matches[1] }
  return 0
}

function Get-JdkHome {
  $candidates = @()
  if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
  $candidates += (Get-ChildItem 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like 'jdk-2*' } | Sort-Object Name -Descending | ForEach-Object FullName)
  foreach ($c in $candidates) {
    $java = Join-Path $c 'bin\java.exe'
    if ((Test-Path $java) -and ((Get-JavaMajor $java) -ge 21)) { return $c }
  }
  throw "No JDK 21+ found. Install one (e.g. winget install Microsoft.OpenJDK.21) and/or set JAVA_HOME."
}

$JdkHome = Get-JdkHome
Write-Host "==> Using JDK: $JdkHome"

Write-Host "==> Building fat JAR (sbt assembly)..."
sbt -batch -java-home "$JdkHome" "assembly"

Write-Host "==> Staging JAR..."
Remove-Item -Recurse -Force stage, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force stage | Out-Null
Copy-Item "target\scala-3.8.3\technical-debt-radar.jar" "stage\"

Write-Host "==> Packaging standalone binary (jpackage)..."
& "$JdkHome\bin\jpackage.exe" `
  --type app-image `
  --name radar `
  --input stage `
  --main-jar technical-debt-radar.jar `
  --main-class radar `
  --win-console `
  --dest dist

Write-Host ""
Write-Host "Done. Standalone binary: dist\radar\radar.exe"
Write-Host "Try it:  .\dist\radar\radar.exe C:\path\to\some\repo"
