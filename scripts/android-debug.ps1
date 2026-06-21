[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('build', 'devices', 'install', 'launch', 'reinstall-launch', 'logcat', 'save-logcat', 'screenshot', 'clear-data')]
  [string] $Action,

  [string] $PackageName = 'com.shiqi.expirytracker',
  [string] $ActivityName = '.MainActivity',
  [string] $ApkPath,
  [string] $DebugDir
)

$ErrorActionPreference = 'Stop'

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptRoot

if (-not $ApkPath) {
  $ApkPath = Join-Path $ProjectRoot 'apk\build\outputs\apk\shiqi-android-debug.apk'
} elseif (-not [System.IO.Path]::IsPathRooted($ApkPath)) {
  $ApkPath = Join-Path $ProjectRoot $ApkPath
}

if (-not $DebugDir) {
  $DebugDir = Join-Path $ProjectRoot 'apk\debug'
} elseif (-not [System.IO.Path]::IsPathRooted($DebugDir)) {
  $DebugDir = Join-Path $ProjectRoot $DebugDir
}

function Get-Timestamp {
  Get-Date -Format 'yyyyMMdd-HHmmss'
}

function Resolve-Adb {
  $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
  if ($adbCommand) {
    return $adbCommand.Source
  }

  $sdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) |
    Where-Object { $_ -and (Test-Path $_) } |
    Select-Object -Unique

  foreach ($sdkRoot in $sdkRoots) {
    $candidate = Join-Path $sdkRoot 'platform-tools\adb.exe'
    if (Test-Path $candidate) {
      return $candidate
    }
  }

  throw 'adb not found. Add Android SDK platform-tools to PATH, or set ANDROID_HOME/ANDROID_SDK_ROOT.'
}

$script:AdbPath = $null

function Get-AdbPath {
  if (-not $script:AdbPath) {
    $script:AdbPath = Resolve-Adb
  }

  $script:AdbPath
}

function Invoke-Checked {
  param(
    [Parameter(Mandatory = $true)]
    [string] $FilePath,

    [string[]] $Arguments = @()
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
  }
}

function Invoke-Adb {
  param(
    [string[]] $Arguments = @()
  )

  Invoke-Checked (Get-AdbPath) $Arguments
}

function Assert-ApkExists {
  if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath. Run the build task first."
  }
}

function New-DebugDirectory {
  New-Item -ItemType Directory -Force -Path $DebugDir | Out-Null
}

function Get-LaunchComponent {
  if ($ActivityName.Contains('/')) {
    return $ActivityName
  }

  "${PackageName}/${ActivityName}"
}

function Invoke-Build {
  $buildScript = Join-Path $ProjectRoot 'apk\build-apk.ps1'
  Invoke-Checked 'powershell' @('-ExecutionPolicy', 'Bypass', '-File', $buildScript)
}

function Invoke-Install {
  Assert-ApkExists
  Invoke-Adb @('install', '-r', $ApkPath)
}

function Invoke-Launch {
  Invoke-Adb @('shell', 'am', 'start', '-n', (Get-LaunchComponent))
}

function Invoke-SaveLogcat {
  New-DebugDirectory
  $logPath = Join-Path $DebugDir "logcat-$(Get-Timestamp).txt"
  $adb = Get-AdbPath
  $logOutput = & $adb @('logcat', '-d', '-v', 'time')
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $adb logcat -d -v time"
  }

  $logOutput | Set-Content -LiteralPath $logPath -Encoding UTF8
  Write-Host "Logcat saved: $logPath"
}

function Invoke-Screenshot {
  New-DebugDirectory
  $timestamp = Get-Timestamp
  $devicePath = "/sdcard/shiqi-debug-screenshot-$timestamp.png"
  $localPath = Join-Path $DebugDir "screenshot-$timestamp.png"

  Invoke-Adb @('shell', 'screencap', '-p', $devicePath)
  Invoke-Adb @('pull', $devicePath, $localPath)

  try {
    Invoke-Adb @('shell', 'rm', $devicePath)
  } catch {
    Write-Warning "Screenshot was pulled, but the temporary device file could not be removed: $devicePath"
  }

  Write-Host "Screenshot saved: $localPath"
}

switch ($Action) {
  'build' {
    Invoke-Build
  }
  'devices' {
    Invoke-Adb @('devices', '-l')
  }
  'install' {
    Invoke-Install
  }
  'launch' {
    Invoke-Launch
  }
  'reinstall-launch' {
    Invoke-Install
    Invoke-Launch
  }
  'logcat' {
    Write-Host 'Streaming Logcat. Press Ctrl+C to stop.'
    Invoke-Adb @('logcat', '-v', 'time')
  }
  'save-logcat' {
    Invoke-SaveLogcat
  }
  'screenshot' {
    Invoke-Screenshot
  }
  'clear-data' {
    Invoke-Adb @('shell', 'pm', 'clear', $PackageName)
  }
}
