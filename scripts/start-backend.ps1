$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$backendDir = Join-Path $repoRoot "backend"
$toolsDir = Join-Path $repoRoot ".tools"
$mavenVersion = "3.9.6"
$mavenBase = "apache-maven-$mavenVersion"
$mavenDir = Join-Path $toolsDir $mavenBase
$zipPath = Join-Path $toolsDir "$mavenBase-bin.zip"
$mavenUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/$mavenBase-bin.zip"
$ytDlpUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
$ytDlpPath = Join-Path $toolsDir "yt-dlp.exe"
$ffmpegUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
$ffmpegZipPath = Join-Path $toolsDir "ffmpeg-release-essentials.zip"
$ffmpegDir = Join-Path $toolsDir "ffmpeg"

function Resolve-MavenCmd {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        return $mvn.Path
    }

    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    if (!(Test-Path (Join-Path $mavenDir "bin\\mvn.cmd"))) {
        if (Test-Path $zipPath) {
            Remove-Item -Force $zipPath
        }
        Invoke-WebRequest -Uri $mavenUrl -OutFile $zipPath -TimeoutSec 120
        Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    }

    $mvnCmd = Join-Path $mavenDir "bin\\mvn.cmd"
    if (!(Test-Path $mvnCmd)) {
        throw "Maven command not found at: $mvnCmd"
    }

    $mvnOk = $false
    try {
        & $mvnCmd -v | Out-Null
        if ($LASTEXITCODE -eq 0) { $mvnOk = $true }
    } catch {
        $mvnOk = $false
    }

    if (-not $mvnOk) {
        if (Test-Path $mavenDir) {
            Remove-Item -Recurse -Force $mavenDir
        }
        if (Test-Path $zipPath) {
            Remove-Item -Force $zipPath
        }
        Invoke-WebRequest -Uri $mavenUrl -OutFile $zipPath -TimeoutSec 120
        Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
        if (!(Test-Path $mvnCmd)) {
            throw "Maven command not found after download: $mvnCmd"
        }
    }

    return $mvnCmd
}

function Find-ProjectFfmpeg {
    $direct = Join-Path $ffmpegDir "bin\ffmpeg.exe"
    if (Test-Path $direct) {
        return (Resolve-Path $direct).Path
    }

    if (Test-Path $toolsDir) {
        $candidate = Get-ChildItem -Path $toolsDir -Recurse -Filter "ffmpeg.exe" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }

    return $null
}

function Resolve-FfmpegCmd {
    $ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($ffmpeg) {
        return $ffmpeg.Path
    }

    $projectFfmpeg = Find-ProjectFfmpeg
    if ($projectFfmpeg) {
        return $projectFfmpeg
    }

    try {
        New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
        Invoke-WebRequest -Uri $ffmpegUrl -OutFile $ffmpegZipPath -TimeoutSec 240
        Expand-Archive -Path $ffmpegZipPath -DestinationPath $ffmpegDir -Force
        return Find-ProjectFfmpeg
    } catch {
        Write-Warning "ffmpeg was not found and automatic download failed: $($_.Exception.Message). The backend will fall back to progressive MP4 downloads."
        return $null
    }
}

$ytDlpCmd = $null
$ytDlp = Get-Command yt-dlp -ErrorAction SilentlyContinue
if ($ytDlp) {
    $ytDlpCmd = $ytDlp.Path
} else {
    if (!(Test-Path $ytDlpPath)) {
        New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
        Invoke-WebRequest -Uri $ytDlpUrl -OutFile $ytDlpPath -TimeoutSec 120
    }
    $ytDlpCmd = $ytDlpPath
}

$mvnCmd = Resolve-MavenCmd
$ffmpegCmd = Resolve-FfmpegCmd
Push-Location $backendDir
try {
    $env:VELP_YTDLP_PATH = $ytDlpCmd
    if ($ffmpegCmd) {
        $env:VELP_FFMPEG_PATH = $ffmpegCmd
    }
    & $mvnCmd clean spring-boot:run
} finally {
    Pop-Location
}
