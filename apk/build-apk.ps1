param(
  [ValidateSet('debug', 'release')]
  [string] $Variant = 'debug'
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AndroidRoot = $PSScriptRoot
$AppRoot = Join-Path $AndroidRoot 'app'
$LibRoot = Join-Path $AppRoot 'libs'
$BuildRoot = Join-Path $AndroidRoot 'build'
$OutputDir = Join-Path $BuildRoot 'outputs\apk'
$FinalApk = Join-Path $OutputDir "shiqi-android-$Variant.apk"

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

  throw "JDK tool '$Name' was not found. Install a JDK and put $Name on PATH, or set JAVA_HOME."
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

$CompiledRes = Join-Path $BuildRoot 'compiled-res'
$Generated = Join-Path $BuildRoot 'generated'
$Classes = Join-Path $BuildRoot 'classes'
$Dex = Join-Path $BuildRoot 'dex'
$UnsignedApk = Join-Path $BuildRoot 'app-unsigned.apk'
$DexApk = Join-Path $BuildRoot 'app-unsigned-dex.apk'
$AlignedApk = Join-Path $BuildRoot 'app-aligned.apk'
$CompiledClassesJar = Join-Path $BuildRoot 'classes.jar'
$Keystore = Join-Path $AndroidRoot 'debug.keystore'
$KeyAlias = 'androiddebugkey'
$KeyStorePass = 'android'
$KeyPass = 'android'
$Javac = Resolve-JavaTool 'javac'
$Jar = Resolve-JavaTool 'jar'
$Keytool = Resolve-JavaTool 'keytool'

if ($Variant -eq 'release') {
  $Keystore = $env:SHIQI_RELEASE_KEYSTORE
  $KeyAlias = $env:SHIQI_RELEASE_KEY_ALIAS
  $KeyStorePass = $env:SHIQI_RELEASE_STORE_PASSWORD
  if (-not $KeyStorePass) {
    $KeyStorePass = $env:SHIQI_RELEASE_KEYSTORE_PASSWORD
  }
  $KeyPass = $env:SHIQI_RELEASE_KEY_PASSWORD
  if (-not $KeyPass) {
    $KeyPass = $KeyStorePass
  }

  if (-not $Keystore -or -not (Test-Path $Keystore)) {
    throw 'Release keystore not found. Set SHIQI_RELEASE_KEYSTORE to a keystore path outside the repository.'
  }
  if (-not $KeyAlias) {
    throw 'Release key alias missing. Set SHIQI_RELEASE_KEY_ALIAS.'
  }
  if (-not $KeyStorePass) {
    throw 'Release keystore password missing. Set SHIQI_RELEASE_STORE_PASSWORD or SHIQI_RELEASE_KEYSTORE_PASSWORD.'
  }
  if (-not $KeyPass) {
    throw 'Release key password missing. Set SHIQI_RELEASE_KEY_PASSWORD, or use the same password as the keystore.'
  }
}

if (Test-Path $BuildRoot) {
  Remove-Item -LiteralPath $BuildRoot -Recurse -Force
}

New-Item -ItemType Directory -Force $CompiledRes, $Generated, $Classes, $Dex, $OutputDir | Out-Null

$LibJars = @()
if (Test-Path $LibRoot) {
  $LibJars = @(Get-ChildItem $LibRoot -Filter *.jar | ForEach-Object { $_.FullName })
}

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

$JavacClasspath = (@($AndroidJar) + $LibJars) -join [IO.Path]::PathSeparator
$JavacArgs = @('-encoding', 'UTF-8', '--release', '8', '-classpath', $JavacClasspath, '-d', $Classes) + $JavaFiles
Invoke-Checked $Javac $JavacArgs

Push-Location $Classes
try {
  Invoke-Checked $Jar @('cf', $CompiledClassesJar, '.')
} finally {
  Pop-Location
}

$D8Args = @('--min-api', '23', '--lib', $AndroidJar, '--output', $Dex, $CompiledClassesJar) + $LibJars
Invoke-Checked $D8 $D8Args

Copy-Item -LiteralPath $UnsignedApk -Destination $DexApk -Force
Push-Location $Dex
try {
  Invoke-Checked $Jar @('uf', $DexApk, 'classes.dex')
} finally {
  Pop-Location
}

if ($Variant -eq 'debug' -and -not (Test-Path $Keystore)) {
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
  Invoke-Checked $Keytool $KeytoolArgs | Out-Null
}

Invoke-Checked $Zipalign @('-f', '4', $DexApk, $AlignedApk)

$SignArgs = @(
  'sign',
  '--ks', $Keystore,
  '--ks-pass', "pass:$KeyStorePass",
  '--key-pass', "pass:$KeyPass",
  '--ks-key-alias', $KeyAlias,
  '--out', $FinalApk,
  $AlignedApk
)
Invoke-Checked $Apksigner $SignArgs

Invoke-Checked $Apksigner @('verify', '--verbose', $FinalApk)

Write-Host "APK built: $FinalApk"
