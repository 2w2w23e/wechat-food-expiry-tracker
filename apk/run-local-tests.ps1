param()

$ErrorActionPreference = 'Stop'

$ApkRoot = $PSScriptRoot
$MainSourceRoot = Join-Path $ApkRoot 'app\src\main\java\com\shiqi\expirytracker'
$TestRoot = Join-Path $ApkRoot 'tests'
$TempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('shiqi-local-tests-' + [System.Guid]::NewGuid().ToString('N'))
$Classes = Join-Path $TempRoot 'classes'

function Resolve-JavaTool {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Name
  )

  $exeName = if ($IsWindows -or $env:OS -eq 'Windows_NT') { "$Name.exe" } else { $Name }

  if ($env:JAVA_HOME) {
    $fromJavaHome = Join-Path (Join-Path $env:JAVA_HOME 'bin') $exeName
    if (Test-Path $fromJavaHome) {
      return $fromJavaHome
    }
  }

  $command = Get-Command $Name -ErrorAction SilentlyContinue
  if ($command) {
    return $command.Source
  }

  throw "JDK tool '$Name' was not found. Install a JDK and put $Name on PATH, or set JAVA_HOME. Android SDK is not required for these tests."
}

function Invoke-Checked {
  param(
    [Parameter(Mandatory = $true)]
    [string] $FilePath,
    [string[]] $Arguments
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
  }
}

$Javac = Resolve-JavaTool 'javac'
$Java = Resolve-JavaTool 'java'

$MainSources = @(
  'DateRules.java',
  'FoodItem.java',
  'Option.java',
  'FoodData.java',
  'DailyBriefing.java',
  'ReminderEvent.java',
  'ReminderPlan.java',
  'ReminderPolicy.java',
  'DateOcrParser.java',
  'DateOcrFrameVoter.java',
  'BarcodeUtils.java',
  'BarcodeHistoryItem.java',
  'FoodExcelExporter.java',
  'FoodExcelImporter.java',
  'FoodStoreMigration.java'
) | ForEach-Object { Join-Path $MainSourceRoot $_ }

$MissingSources = @($MainSources | Where-Object { -not (Test-Path $_) })
if ($MissingSources.Count -gt 0) {
  throw "Missing source file(s): $($MissingSources -join ', ')"
}

$TestSources = @()
$TestSources += Get-ChildItem (Join-Path $TestRoot 'stubs') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$TestSources += Get-ChildItem (Join-Path $TestRoot 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }

if ($TestSources.Count -eq 0) {
  throw "No local test sources found under $TestRoot."
}

try {
  New-Item -ItemType Directory -Force $Classes | Out-Null

  Write-Host 'Compiling APK local Java logic tests (Android SDK not required)...'
  $JavacArgs = @(
    '-encoding', 'UTF-8',
    '-source', '8',
    '-target', '8',
    '-d', $Classes
  ) + $MainSources + $TestSources
  Invoke-Checked $Javac $JavacArgs

  Write-Host 'Running APK local Java logic tests...'
  Invoke-Checked $Java @('-cp', $Classes, 'com.shiqi.expirytracker.LocalLogicTest')
} finally {
  if (Test-Path $TempRoot) {
    Remove-Item -LiteralPath $TempRoot -Recurse -Force
  }
}
