$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AndroidRoot = $PSScriptRoot
$AppRoot = Join-Path $AndroidRoot 'app'
$BuildRoot = Join-Path $AndroidRoot 'build'
$OutputDir = Join-Path $BuildRoot 'outputs\apk'
$FinalApk = Join-Path $OutputDir 'shiqi-android-debug.apk'

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

$SdkRoot = $env:ANDROID_HOME
if (-not $SdkRoot) {
  $SdkRoot = $env:ANDROID_SDK_ROOT
}

if (-not $SdkRoot -or -not (Test-Path $SdkRoot)) {
  throw 'Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT.'
}

$BuildToolsDir = Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory |
  Sort-Object Name -Descending |
  Select-Object -First 1

if (-not $BuildToolsDir) {
  throw 'Android SDK build-tools not found.'
}

$PlatformDir = Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory |
  Sort-Object Name -Descending |
  Select-Object -First 1

if (-not $PlatformDir) {
  throw 'Android SDK platform android.jar not found.'
}

$AndroidJar = Join-Path $PlatformDir.FullName 'android.jar'
$Aapt2 = Join-Path $BuildToolsDir.FullName 'aapt2.exe'
$D8 = Join-Path $BuildToolsDir.FullName 'd8.bat'
$Zipalign = Join-Path $BuildToolsDir.FullName 'zipalign.exe'
$Apksigner = Join-Path $BuildToolsDir.FullName 'apksigner.bat'

foreach ($tool in @($AndroidJar, $Aapt2, $D8, $Zipalign, $Apksigner)) {
  if (-not (Test-Path $tool)) {
    throw "Required Android build tool missing: $tool"
  }
}

if (Test-Path $BuildRoot) {
  Remove-Item -LiteralPath $BuildRoot -Recurse -Force
}

$CompiledRes = Join-Path $BuildRoot 'compiled-res'
$Generated = Join-Path $BuildRoot 'generated'
$Classes = Join-Path $BuildRoot 'classes'
$Dex = Join-Path $BuildRoot 'dex'
$UnsignedApk = Join-Path $BuildRoot 'app-unsigned.apk'
$DexApk = Join-Path $BuildRoot 'app-unsigned-dex.apk'
$AlignedApk = Join-Path $BuildRoot 'app-aligned.apk'
$Keystore = Join-Path $AndroidRoot 'debug.keystore'

New-Item -ItemType Directory -Force $CompiledRes, $Generated, $Classes, $Dex, $OutputDir | Out-Null

$CompileArgs = @('compile', '--dir', (Join-Path $AppRoot 'src\main\res'), '-o', $CompiledRes)
Invoke-Checked $Aapt2 $CompileArgs

$CompiledFiles = @(Get-ChildItem $CompiledRes -Recurse -Filter *.flat | ForEach-Object { $_.FullName })
$LinkArgs = @(
  'link',
  '-o', $UnsignedApk,
  '-I', $AndroidJar,
  '--manifest', (Join-Path $AppRoot 'src\main\AndroidManifest.xml'),
  '--java', $Generated,
  '--min-sdk-version', '23',
  '--target-sdk-version', '35'
)

foreach ($file in $CompiledFiles) {
  $LinkArgs += $file
}

Invoke-Checked $Aapt2 $LinkArgs

$JavaFiles = @()
$JavaFiles += Get-ChildItem (Join-Path $AppRoot 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$JavaFiles += Get-ChildItem $Generated -Recurse -Filter *.java | ForEach-Object { $_.FullName }

$JavacArgs = @('-encoding', 'UTF-8', '--release', '8', '-classpath', $AndroidJar, '-d', $Classes) + $JavaFiles
Invoke-Checked 'javac' $JavacArgs

$ClassFiles = @(Get-ChildItem $Classes -Recurse -Filter *.class | ForEach-Object { $_.FullName })
$D8Args = @('--min-api', '23', '--lib', $AndroidJar, '--output', $Dex) + $ClassFiles
Invoke-Checked $D8 $D8Args

Copy-Item -LiteralPath $UnsignedApk -Destination $DexApk -Force
Push-Location $Dex
try {
  Invoke-Checked 'jar' @('uf', $DexApk, 'classes.dex')
} finally {
  Pop-Location
}

if (-not (Test-Path $Keystore)) {
  $KeytoolArgs = @(
    '-genkeypair',
    '-keystore', $Keystore,
    '-storepass', 'android',
    '-keypass', 'android',
    '-alias', 'androiddebugkey',
    '-dname', 'CN=Android Debug,O=Android,C=US',
    '-keyalg', 'RSA',
    '-keysize', '2048',
    '-validity', '10000'
  )
  Invoke-Checked 'keytool' $KeytoolArgs | Out-Null
}

Invoke-Checked $Zipalign @('-f', '4', $DexApk, $AlignedApk)

$SignArgs = @(
  'sign',
  '--ks', $Keystore,
  '--ks-pass', 'pass:android',
  '--key-pass', 'pass:android',
  '--out', $FinalApk,
  $AlignedApk
)
Invoke-Checked $Apksigner $SignArgs

Invoke-Checked $Apksigner @('verify', '--verbose', $FinalApk)

Write-Host "APK built: $FinalApk"
