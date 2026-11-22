$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$evidenceDir = Join-Path $projectRoot 'artifacts/c09'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
Push-Location (Join-Path $projectRoot 'src')
try {
    function Invoke-Maven {
        if ($env:OS -eq 'Windows_NT') { & .\mvnw.cmd @args }
        else { & bash ./mvnw @args }
    }
    $revision = git rev-parse HEAD
    $dirty = @(git status --porcelain).Count -gt 0
    @("commit=$revision", "dirty=$dirty", "time=$([DateTimeOffset]::UtcNow.ToString('o'))", "schema=V1-V3", 'command=mvnw clean verify') | Set-Content (Join-Path $evidenceDir 'manifest.txt')
    java -version 2>&1 | Out-File (Join-Path $evidenceDir 'runtime.txt')
    Invoke-Maven --version 2>&1 | Out-File (Join-Path $evidenceDir 'maven.txt')
    Invoke-Maven -B clean verify 2>&1 | Tee-Object (Join-Path $evidenceDir 'verify.log')
    $buildExit = $LASTEXITCODE
    "exitCode=$buildExit" | Add-Content (Join-Path $evidenceDir 'manifest.txt')
    $reportsDir = Join-Path $evidenceDir 'surefire-reports'
    New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null
    if (Test-Path 'target/surefire-reports') {
        Copy-Item 'target/surefire-reports/*' $reportsDir -Force
    }
    if ($buildExit -ne 0) { throw "Build/test failed: $buildExit" }
    Invoke-Maven -B dependency:tree "-DoutputFile=$evidenceDir/dependencies.txt" 2>&1 | Out-File (Join-Path $evidenceDir 'dependencies.log')
    if ($LASTEXITCODE -ne 0) { throw 'Dependency inventory failed' }
    Get-ChildItem 'target/*.jar' | ForEach-Object {
        $digest = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        "artifact=$($_.Name) SHA256=$($digest.Hash)" | Add-Content (Join-Path $evidenceDir 'manifest.txt')
        Copy-Item -LiteralPath $_.FullName -Destination $evidenceDir -Force
    }
} finally { Pop-Location }
