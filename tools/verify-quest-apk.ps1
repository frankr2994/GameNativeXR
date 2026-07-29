[CmdletBinding()]
param(
    [string]$ApkPath = "app\build\outputs\apk\debug\app-debug.apk",
    [string]$SdkRoot,
    [string]$AdbPath,
    [string]$DeviceSerial,
    [string]$ReportPath = "build\verification\quest-apk-report.txt",
    [switch]$Install,
    [switch]$Launch,
    [switch]$RequireDevice
)

$ErrorActionPreference = "Stop"

$expectedPackage = "app.gamenative"
$expectedVrActivity = "app.gamenative/com.winlator.xr.runtime.MetaQuest"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Resolve-RelativePath([string]$Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Get-SdkRoot {
    param([string]$ConfiguredRoot)

    if ($ConfiguredRoot) {
        return $ConfiguredRoot
    }
    if ($env:ANDROID_SDK_ROOT) {
        return $env:ANDROID_SDK_ROOT
    }
    if ($env:ANDROID_HOME) {
        return $env:ANDROID_HOME
    }

    $localProperties = Join-Path $repoRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkLine) {
            return ($sdkLine -replace '^sdk\.dir=', '' -replace '\\:', ':' -replace '\\', '\\')
        }
    }

    throw "Android SDK root was not found. Pass -SdkRoot or set ANDROID_SDK_ROOT."
}

function Get-BuildTool([string]$AndroidSdkRoot, [string]$Name) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $AndroidSdkRoot "build-tools") -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName $Name } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
    if (-not $candidate) {
        throw "Unable to find $Name below $AndroidSdkRoot\\build-tools."
    }
    return $candidate
}

function Invoke-External([string]$FilePath, [string[]]$Arguments) {
    $result = & $FilePath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $FilePath $($Arguments -join ' ')`n$result"
    }
    return ($result | Out-String).TrimEnd()
}

function Get-AdbArguments([string[]]$Arguments) {
    if ($DeviceSerial) {
        return @("-s", $DeviceSerial) + $Arguments
    }
    return $Arguments
}

$resolvedApk = Resolve-RelativePath $ApkPath
$resolvedReport = Resolve-RelativePath $ReportPath
$androidSdkRoot = Get-SdkRoot $SdkRoot
$aapt = Get-BuildTool $androidSdkRoot "aapt.exe"
$apksigner = Get-BuildTool $androidSdkRoot "apksigner.bat"

if (-not (Test-Path -LiteralPath $resolvedApk)) {
    throw "APK not found: $resolvedApk. Build it first with .\\gradlew.bat assembleDebug."
}

$badging = Invoke-External $aapt @("dump", "badging", $resolvedApk)
$manifestTree = Invoke-External $aapt @("dump", "xmltree", $resolvedApk, "AndroidManifest.xml")
$signing = Invoke-External $apksigner @("verify", "--verbose", "--print-certs", $resolvedApk)
$badgingLines = $badging -split "`r?`n"
$packageLine = $badgingLines | Select-String "^package:"
$nativeCodeLine = $badgingLines | Select-String "^\s*native-code:"
$launchableLine = $badgingLines | Select-String "^launchable-activity:"
$packageName = if ($packageLine -match "name='([^']+)'") { $Matches[1] } else { "<not found>" }
$versionName = if ($packageLine -match "versionName='([^']*)'") { $Matches[1] } else { "<not found>" }
$versionCode = if ($packageLine -match "versionCode='([^']*)'") { $Matches[1] } else { "<not found>" }
$abiLine = if ($nativeCodeLine) { $nativeCodeLine.Line } else { "<no native-code declaration>" }
$hasArm64 = $abiLine -match "arm64-v8a"
$hasMetaQuestActivity = $manifestTree -match "com.winlator.xr.runtime.MetaQuest"
$hasQuestVrCategory = $manifestTree -match "com.oculus.intent.category.VR"
$hash = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash
$apkItem = Get-Item -LiteralPath $resolvedApk
$gitCommit = (Invoke-External "git" @("rev-parse", "HEAD"))
$gitState = & git status --short 2>&1 | Out-String

$checks = @(
    [PSCustomObject]@{ Name = "Expected package"; Pass = $packageName -eq $expectedPackage; Detail = $packageName }
    [PSCustomObject]@{ Name = "ARM64 native libraries"; Pass = $hasArm64; Detail = $abiLine }
    [PSCustomObject]@{ Name = "Meta Quest VR activity"; Pass = $hasMetaQuestActivity; Detail = "com.winlator.xr.runtime.MetaQuest" }
    [PSCustomObject]@{ Name = "Quest VR category"; Pass = $hasQuestVrCategory; Detail = "com.oculus.intent.category.VR" }
    [PSCustomObject]@{ Name = "APK signing"; Pass = $signing -match "Verified"; Detail = ($signing -split "`r?`n" | Select-Object -First 1) }
)

$deviceSummary = "Not checked"
if ($Install -or $Launch -or $RequireDevice) {
    if (-not $AdbPath) {
        $AdbPath = Join-Path $androidSdkRoot "platform-tools\adb.exe"
    }
    if (-not (Test-Path -LiteralPath $AdbPath)) {
        throw "adb was not found: $AdbPath. Pass -AdbPath."
    }
    $deviceSummary = Invoke-External $AdbPath (Get-AdbArguments @("devices", "-l"))
    if ($deviceSummary -notmatch "\tdevice") {
        throw "No authorized Android device is connected. Enable USB debugging and authorize this computer."
    }
    if ($Install) {
        Invoke-External $AdbPath (Get-AdbArguments @("install", "-r", $resolvedApk)) | Out-Null
    }
    if ($Launch) {
        if (-not $Install) {
            Invoke-External $AdbPath (Get-AdbArguments @("shell", "pm", "path", $expectedPackage)) | Out-Null
        }
        Invoke-External $AdbPath (Get-AdbArguments @("shell", "am", "start", "-n", $expectedVrActivity)) | Out-Null
    }
}

$reportDirectory = Split-Path -Parent $resolvedReport
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$report = @(
    "GameNativeXR Quest APK verification report",
    "Generated: $(Get-Date -Format o)",
    "Repository commit: $gitCommit",
    "Repository dirty state:",
    ($gitState.TrimEnd()),
    "",
    "APK: $resolvedApk",
    "Size bytes: $($apkItem.Length)",
    "Last write: $($apkItem.LastWriteTime.ToString('o'))",
    "SHA-256: $hash",
    "Package: $packageName",
    "Version: $versionName ($versionCode)",
    "Launchable activity: $launchableLine",
    "",
    "Checks:"
)
$report += $checks | ForEach-Object { "[$(if ($_.Pass) { 'PASS' } else { 'FAIL' })] $($_.Name): $($_.Detail)" }
$report += @("", "ADB/device status:", $deviceSummary)
$report | Set-Content -LiteralPath $resolvedReport -Encoding utf8

$report | ForEach-Object { Write-Host $_ }
if ($checks.Pass -contains $false) {
    exit 1
}
