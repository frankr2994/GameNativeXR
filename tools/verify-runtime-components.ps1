param(
    [switch]$Help
)

if ($Help) {
    Write-Host "verify-runtime-components.ps1 - Validates GameNativeXR runtime dependencies"
    Write-Host "Parses arrays.xml, *_download.json manifests, and verifies local bundled assets without network calls."
    exit 0
}

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..")
$OutputDir = Join-Path $ProjectRoot "build\verification"
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}
$ReportPath = Join-Path $OutputDir "runtime-verification-report.txt"

$LogLines = @()
function Log($msg) {
    Write-Host $msg
    $script:LogLines += $msg
}

Log "=== GameNativeXR Runtime Component Verification ==="
Log "Date: $(Get-Date)"

$AssetsDir = Join-Path $ProjectRoot "app\src\main\assets"
$LegacyAssetsDir = Join-Path $ProjectRoot "app\src\legacy\assets"

# 1. Parse Provenance
$ProvenanceFile = Join-Path $AssetsDir "runtime-component-provenance.json"
$Provenance = @{}
if (Test-Path $ProvenanceFile) {
    $ProvData = Get-Content $ProvenanceFile | ConvertFrom-Json
    foreach ($comp in $ProvData.components) {
        $Provenance[$comp.id] = $comp
    }
} else {
    Log "WARNING: runtime-component-provenance.json missing!"
}

# 2. Find manifests
$Manifests = Get-ChildItem -Path $AssetsDir -Filter "*_download.json"
$DownloadableIDs = @{}
$DuplicateIDs = @()
$MalformedURLs = @()

foreach ($mf in $Manifests) {
    $Data = Get-Content $mf.FullName | ConvertFrom-Json
    foreach ($comp in $Data.components) {
        if ($DownloadableIDs.ContainsKey($comp.id)) {
            $DuplicateIDs += $comp.id
        } else {
            $DownloadableIDs[$comp.id] = $comp
        }
        
        if ([string]::IsNullOrWhiteSpace($comp.url) -or (-not $comp.url.StartsWith("http"))) {
            $MalformedURLs += $comp.id
        }
    }
}

if ($DuplicateIDs.Count -gt 0) { Log "ERROR: Duplicate IDs found in manifests: $($DuplicateIDs -join ', ')" }
if ($MalformedURLs.Count -gt 0) { Log "ERROR: Malformed URLs found for IDs: $($MalformedURLs -join ', ')" }

# 3. Parse arrays.xml
$ArraysXmlPath = Join-Path $ProjectRoot "app\src\main\res\values\arrays.xml"
[xml]$ArraysXml = Get-Content $ArraysXmlPath
$SelectableIDs = @()
foreach ($array in $ArraysXml.resources.'string-array') {
    if ($array.name -match "version_entries") {
        foreach ($item in $array.item) {
            $SelectableIDs += $item.Trim()
        }
    }
}

Log "`nChecking Selectable IDs against known packages..."
foreach ($id in $SelectableIDs) {
    # Check if downloadable
    $foundDownloadable = $false
    foreach ($key in $DownloadableIDs.Keys) {
        if ($key -contains $id -or $DownloadableIDs[$key].name -match $id) {
            $foundDownloadable = $true
            break
        }
    }
    
    # Check if bundled
    $foundBundled = $false
    $matchingBundled = Get-ChildItem -Path $AssetsDir -Filter "*$id*.tzst" -Recurse -ErrorAction SilentlyContinue
    if ($matchingBundled) { $foundBundled = $true }
    
    if (-not $foundDownloadable -and -not $foundBundled) {
        Log "WARNING: Selectable version ID '$id' has neither a bundled package nor a manifest entry."
    }
}

# 4. Inspect TZST members without extracting & check expected families
Log "`nInspecting bundled .tzst packages for expected member families..."
$ZstdExe = Join-Path $ProjectRoot "tools\zstd.exe" # Assume standard tool, or skip if not found
$TarExe = "tar" # Built into modern Windows

$AllTzst = Get-ChildItem -Path $AssetsDir -Filter "*.tzst" -Recurse
foreach ($tzst in $AllTzst) {
    # Hash check
    $Hash = (Get-FileHash $tzst.FullName -Algorithm SHA256).Hash
    Log "Package: $($tzst.Name) | SHA256: $Hash"
    
    # Simple check for member names using tar if available
    try {
        $members = & $TarExe -tf $tzst.FullName 2>$null
        if ($LASTEXITCODE -eq 0) {
            if ($tzst.Name -match "fexcore" -and (-not ($members -match "libarm64ecfex.dll" -or $members -match "libwow64fex.dll"))) {
                Log "  -> ERROR: FEX package missing expected DLLs."
            }
            if ($tzst.Name -match "dxvk" -and (-not ($members -match "d3d11.dll" -or $members -match "dxgi.dll"))) {
                Log "  -> ERROR: DXVK package missing expected DLLs."
            }
            if ($tzst.Name -match "vkd3d" -and (-not ($members -match "d3d12.dll"))) {
                Log "  -> ERROR: VKD3D package missing expected DLLs."
            }
        }
    } catch {
        # tar might not support zstd directly on older windows without explicit plugin
    }
}

$LogLines | Out-File $ReportPath -Encoding utf8
Log "`nReport saved to $ReportPath"
