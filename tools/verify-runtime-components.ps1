param(
    [switch]$Help
)

if ($Help) {
    Write-Host 'verify-runtime-components.ps1 - offline validation for GameNativeXR runtime assets'
    exit 0
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir '..')).Path
$MainAssets = Join-Path $ProjectRoot 'app\src\main\assets'
$LegacyAssets = Join-Path $ProjectRoot 'app\src\legacy\assets'
$ReportDir = Join-Path $ProjectRoot 'build\verification'
$ReportPath = Join-Path $ReportDir 'runtime-verification-report.txt'

$Lines = [System.Collections.Generic.List[string]]::new()
$Failures = [System.Collections.Generic.List[string]]::new()
$Warnings = [System.Collections.Generic.List[string]]::new()

function Write-Report([string]$Message) {
    Write-Host $Message
    $script:Lines.Add($Message)
}

function Add-Failure([string]$Message) {
    $script:Failures.Add($Message)
    Write-Report "ERROR: $Message"
}

function Add-Warning([string]$Message) {
    $script:Warnings.Add($Message)
    Write-Report "WARNING: $Message"
}

function Get-NormalizedVersion([string]$Value) {
    return ($Value -replace '\s+\(Default\)$', '').Trim()
}

function Get-ArchiveFiles {
    return @(Get-ChildItem -Path $MainAssets, $LegacyAssets -Filter '*.tzst' -Recurse -File)
}

function Test-ManifestUrl([string]$Id, [string]$Url) {
    $uri = $null
    if (-not [Uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -notin @('http', 'https')) {
        Add-Failure "Manifest component '$Id' has an invalid URL."
    }
}

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
Write-Report '=== GameNativeXR Runtime Component Verification ==='
Write-Report "Date: $(Get-Date -Format o)"

$ArchiveFiles = Get-ArchiveFiles
$ArchiveNames = @{}
foreach ($archive in $ArchiveFiles) {
    $ArchiveNames[$archive.Name] = $true
}

Write-Report ''
Write-Report 'Checking download manifests...'
$ManifestComponents = @{}
foreach ($manifest in Get-ChildItem -Path $MainAssets -Filter '*_download.json' -File) {
    try {
        $data = Get-Content -Raw -LiteralPath $manifest.FullName | ConvertFrom-Json
    } catch {
        Add-Failure "Unable to parse manifest '$($manifest.Name)': $($_.Exception.Message)"
        continue
    }

    foreach ($component in @($data.components)) {
        if ([string]::IsNullOrWhiteSpace($component.id)) {
            Add-Failure "Manifest '$($manifest.Name)' contains a component without an id."
            continue
        }
        if ($ManifestComponents.ContainsKey($component.id)) {
            Add-Failure "Duplicate manifest id '$($component.id)' in '$($manifest.Name)' and '$($ManifestComponents[$component.id].Manifest)'."
            continue
        }
        Test-ManifestUrl $component.id $component.url
        $ManifestComponents[$component.id] = [pscustomobject]@{ Manifest = $manifest.Name; Component = $component }
    }
}

[xml]$arraysXml = Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot 'app\src\main\res\values\arrays.xml')
$ArraysByName = @{}
foreach ($array in @($arraysXml.resources.'string-array')) {
    $ArraysByName[$array.name] = @($array.item | ForEach-Object { $_.Trim() })
}

$SelectorSpecs = @(
    [pscustomobject]@{ Array = 'wrapper_graphics_driver_version_entries'; Id = { param($v) if ($v -eq 'System') { $null } else { "adrenotools-$v" } }; Archive = { param($v) if ($v -eq 'System') { $null } else { "adrenotools-$v.tzst" } } },
    [pscustomobject]@{ Array = 'turnip_version_entries'; Id = { param($v) "turnip-$v" }; Archive = { param($v) "turnip-$v.tzst" } },
    [pscustomobject]@{ Array = 'virgl_version_entries'; Id = { param($v) "virgl-$v" }; Archive = { param($v) "virgl-$v.tzst" } },
    [pscustomobject]@{ Array = 'zink_version_entries'; Id = { param($v) "zink-$v" }; Archive = { param($v) "zink-$v.tzst" } },
    [pscustomobject]@{ Array = 'vortek_version_entries'; Id = { param($v) "vortek-$v" }; Archive = { param($v) "vortek-$v.tzst" } },
    [pscustomobject]@{ Array = 'adreno_version_entries'; Id = { param($v) "Adreno_" + $v + "_adpkg" }; Archive = { param($v) $null } },
    [pscustomobject]@{ Array = 'sd8elite_version_entries'; Id = { param($v) "SD8Elite_$v" }; Archive = { param($v) $null } },
    [pscustomobject]@{ Array = 'dxvk_version_entries'; Id = { param($v) if ($v -like 'async-*') { "dxvk-async-$($v.Substring(6))" } else { "dxvk-$v" } }; Archive = { param($v) if ($v -like 'async-*') { "dxvk-async-$($v.Substring(6)).tzst" } else { "dxvk-$v.tzst" } } },
    [pscustomobject]@{ Array = 'vkd3d_version_entries'; Id = { param($v) "vkd3d-$v" }; Archive = { param($v) "vkd3d-$v.tzst" } },
    [pscustomobject]@{ Array = 'box64_version_entries'; Id = { param($v) $null }; Archive = { param($v) "box64-$v.tzst" } },
    [pscustomobject]@{ Array = 'box64_bionic_version_entries'; Id = { param($v) $null }; Archive = { param($v) "box64-$v-bionic.tzst" } },
    [pscustomobject]@{ Array = 'wowbox64_version_entries'; Id = { param($v) $null }; Archive = { param($v) "wowbox64-$v.tzst" } },
    [pscustomobject]@{ Array = 'fexcore_version_entries'; Id = { param($v) $null }; Archive = { param($v) "fexcore-$v.tzst" } }
)

Write-Report ''
Write-Report 'Checking component-specific selectors...'
foreach ($spec in $SelectorSpecs) {
    if (-not $ArraysByName.ContainsKey($spec.Array)) {
        Add-Failure "Required resource array '$($spec.Array)' is missing."
        continue
    }
    foreach ($rawVersion in $ArraysByName[$spec.Array]) {
        $version = Get-NormalizedVersion $rawVersion
        $expectedId = & $spec.Id $version
        $expectedArchive = & $spec.Archive $version
        if ($null -eq $expectedId -and $null -eq $expectedArchive) {
            continue
        }
        $hasManifest = $expectedId -and $ManifestComponents.ContainsKey($expectedId)
        $hasArchive = $expectedArchive -and $ArchiveNames.ContainsKey($expectedArchive)
        if (-not $hasManifest -and -not $hasArchive) {
            Add-Failure "Selector '$($spec.Array):$rawVersion' has neither manifest id '$expectedId' nor archive '$expectedArchive'."
        }
    }
}

Write-Report ''
Write-Report 'Checking archive readability and required runtime members...'
foreach ($archive in $ArchiveFiles) {
    $members = & tar -tf $archive.FullName 2>&1
    if ($LASTEXITCODE -ne 0) {
        Add-Failure "Archive '$($archive.FullName)' is not readable by tar: $($members -join ' ')"
        continue
    }
    if ($archive.Name -like 'fexcore-*' -and ((@($members) -notmatch '(^|/)libarm64ecfex\.dll$').Count -eq 0 -or (@($members) -notmatch '(^|/)libwow64fex\.dll$').Count -eq 0)) {
        Add-Failure "FEX archive '$($archive.Name)' is missing libarm64ecfex.dll or libwow64fex.dll."
    }
    if ($archive.Name -like 'dxvk-*' -and ((@($members) -notmatch '(^|/)d3d11\.dll$').Count -eq 0 -or (@($members) -notmatch '(^|/)dxgi\.dll$').Count -eq 0)) {
        Add-Failure "DXVK archive '$($archive.Name)' is missing d3d11.dll or dxgi.dll."
    }
    if ($archive.Name -like 'vkd3d-*' -and (@($members) -notmatch '(^|/)d3d12\.dll$').Count -eq 0) {
        Add-Failure "VKD3D archive '$($archive.Name)' is missing d3d12.dll."
    }
}

Write-Report ''
Write-Report 'Checking recorded local provenance...'
$provenancePath = Join-Path $MainAssets 'runtime-component-provenance.json'
try {
    $provenance = Get-Content -Raw -LiteralPath $provenancePath | ConvertFrom-Json
} catch {
    Add-Failure "Unable to parse runtime-component-provenance.json: $($_.Exception.Message)"
    $provenance = $null
}
if ($null -ne $provenance) {
    $provenanceIds = @{}
    foreach ($component in @($provenance.components)) {
        if ($provenanceIds.ContainsKey($component.id)) {
            Add-Failure "Duplicate provenance id '$($component.id)'."
            continue
        }
        $provenanceIds[$component.id] = $true
        if ($component.status -eq 'locally_hashed') {
            if ([string]::IsNullOrWhiteSpace($component.local_path) -or [string]::IsNullOrWhiteSpace($component.sha256)) {
                Add-Failure "Locally hashed component '$($component.id)' lacks local_path or sha256."
                continue
            }
            $candidatePath = Join-Path $MainAssets $component.local_path
            if (-not (Test-Path -LiteralPath $candidatePath)) {
                Add-Failure "Provenance path for '$($component.id)' does not exist: $($component.local_path)."
                continue
            }
            $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $candidatePath).Hash
            if ($actualHash -ne $component.sha256) {
                Add-Failure "SHA-256 mismatch for '$($component.id)'."
            }
        }
    }
}

Write-Report ''
Write-Report "Summary: $($Failures.Count) error(s), $($Warnings.Count) warning(s)."
$Lines | Set-Content -LiteralPath $ReportPath -Encoding utf8
Write-Report "Report saved to $ReportPath"
if ($Failures.Count -gt 0) { exit 1 }
