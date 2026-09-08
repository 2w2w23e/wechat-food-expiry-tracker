param(
    [Parameter(Mandatory = $true)] [string] $Adb,
    [Parameter(Mandatory = $true)] [string] $Sqlite,
    [Parameter(Mandatory = $true)] [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
$package = 'com.shiqi.expirytracker'
$database = 'shiqi_inventory_v1.db'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

# Copy only the QA application's Room database after its process has been stopped.
foreach ($suffix in @('', '-wal', '-shm')) {
    $name = "$database$suffix"
    $null = & $Adb shell run-as $package ls "databases/$name"
    if ($LASTEXITCODE -ne 0) {
        if ($suffix -eq '') { throw 'QA inventory database is missing.' }
        continue
    }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Adb
    $start.Arguments = "exec-out run-as $package cat databases/$name"
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $process = [Diagnostics.Process]::Start($start)
    $stream = [IO.File]::Create((Join-Path $OutputDirectory $name))
    try {
        $process.StandardOutput.BaseStream.CopyTo($stream)
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Failed to copy $name" }
    } finally {
        $stream.Dispose()
        $process.Dispose()
    }
}

$path = Join-Path $OutputDirectory $database
$snapshot = [ordered]@{}
foreach ($table in @('products', 'batches', 'barcodes', 'product_barcodes',
        'batch_reminder_plans', 'inventory_metadata')) {
    $rows = & $Sqlite -json $path "SELECT * FROM $table ORDER BY rowid;"
    if ($LASTEXITCODE -ne 0) { throw "Could not read $table" }
    $snapshot[$table] = if ($rows) { @(($rows -join "`n") | ConvertFrom-Json) } else { @() }
}
$json = $snapshot | ConvertTo-Json -Depth 20
$json | Out-File (Join-Path $OutputDirectory 'inventory.json') -Encoding utf8
Get-FileHash (Join-Path $OutputDirectory 'inventory.json') -Algorithm SHA256
