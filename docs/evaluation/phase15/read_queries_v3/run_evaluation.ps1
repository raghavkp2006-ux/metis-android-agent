param(
    [string]$Python = 'python',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$taskRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
Push-Location -LiteralPath $taskRepoRoot
try {
    & $Python -m unittest discover -s docs/evaluation/phase15/read_queries_v3 -p 'test_*.py' -v
    if ($LASTEXITCODE -ne 0) { throw 'Read-query evaluation checks failed.' }
    $taskGradleArgs = @('--no-daemon', ':app:phase15ReadRulesSnapshot')
    if ($Offline) { $taskGradleArgs = @('--offline') + $taskGradleArgs }
    & .\gradlew.bat @taskGradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Native read-query rules snapshot failed.' }
    & $Python docs/evaluation/phase15/read_queries_v3/evaluate.py
    if ($LASTEXITCODE -ne 0) { throw 'Read-query evaluation failed.' }
    Write-Output 'Report: app/build/reports/phase15/results-v3.json. User review and deployment gates remain required.'
}
finally {
    Pop-Location
}
