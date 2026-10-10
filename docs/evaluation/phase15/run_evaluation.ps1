param(
    [string]$Python = 'python',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$taskRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Push-Location -LiteralPath $taskRepoRoot
try {
    & $Python -m unittest discover -s docs/evaluation/phase15 -p 'test_*.py' -v
    if ($LASTEXITCODE -ne 0) { throw 'Phase 15 evaluation integrity checks failed.' }
    $taskGradleArgs = @('--no-daemon', ':app:phase15RulesSnapshot')
    if ($Offline) { $taskGradleArgs = @('--offline') + $taskGradleArgs }
    & .\gradlew.bat @taskGradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Native EnglishRules snapshot failed.' }
    & $Python docs/evaluation/phase15/evaluate.py
    if ($LASTEXITCODE -ne 0) { throw 'Phase 15 evaluation failed.' }
    Write-Output 'Report: app/build/reports/phase15/results-v2.json. A successful run does not enable a model; inspect its decision and gates.'
}
finally {
    Pop-Location
}
