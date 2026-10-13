[CmdletBinding()]
param([string]$GameDirectory = '', [int]$LockTimeoutSeconds = 0)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Run with PowerShell 7 (pwsh); the managed session wrapper requires it.' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$runtime = if ($GameDirectory) {
    if ([IO.Path]::IsPathRooted($GameDirectory)) { [IO.Path]::GetFullPath($GameDirectory) }
    else { [IO.Path]::GetFullPath((Join-Path $root $GameDirectory)) }
} else { Join-Path $root 'runs/gametests' }
$output = Join-Path $root 'build/minecraft-mod-testing'
$harness = Join-Path $output 'gametest-harness.json'
if ($env:MINECRAFT_MOD_TEST_SINGLE_INSTANCE -ne '1') {
    & (Join-Path $root 'mod-build.ps1') ensure
    if ($LASTEXITCODE -ne 0) { throw 'Managed integrity gate failed' }
    $arguments = @('-NoProfile','-File',$PSCommandPath,'-GameDirectory',$runtime,'-LockTimeoutSeconds',"$LockTimeoutSeconds")
    & (Join-Path $root '.agents/skills/minecraft-mod-testing/scripts/invoke_single_session.ps1') `
        -ProjectRoot $root -Executable (Get-Command pwsh).Source -ArgumentList $arguments `
        -RuntimeKind gametest-server -RuntimeDirectory $runtime -HarnessReceiptPath $harness `
        -ExpectedScenarioCount 1 -LockTimeoutSeconds $LockTimeoutSeconds
    exit $LASTEXITCODE
}
New-Item -ItemType Directory -Force -Path $runtime,$output | Out-Null
$session = $env:MINECRAFT_MOD_TEST_SESSION_ID
$manifest = Join-Path $output "sources-$session.json"
$inputs = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -File) +
    @(Get-Item -LiteralPath (Join-Path $root 'build.gradle'), (Join-Path $root 'gradle.properties'),
        $PSCommandPath, (Join-Path $root '.minecraft-mod-testing/install.json'))
@($inputs | ForEach-Object {
    [ordered]@{path=[IO.Path]::GetRelativePath($root,$_.FullName);sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}
}) | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifest -Encoding utf8
Remove-Item -LiteralPath $harness -ErrorAction SilentlyContinue
$started = [DateTime]::UtcNow
Push-Location $root
try {
    & .\gradlew.bat runGameTestServer --no-daemon --console=plain "-PminecraftModTestingRuntimeDirectory=$runtime" '-Djava.awt.headless=true'
    $code = $LASTEXITCODE
    $logPath = Join-Path $runtime 'logs/latest.log'
    $log = if (Test-Path -LiteralPath $logPath) { Get-Content -LiteralPath $logPath -Raw } else { '' }
    $match = [regex]::Match($log,'All (\d+) required tests passed')
    $expected = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/main/java') -Recurse -Filter '*Tests.java' |
        Select-String -Pattern '^\s*@GameTest\(').Count
    $passed = $code -eq 0 -and $match.Success -and [int]$match.Groups[1].Value -eq $expected
    $artifacts = @()
    if (Test-Path -LiteralPath $logPath) {
        $savedLog = Join-Path $output "gametest-$session.log"
        Copy-Item -LiteralPath $logPath -Destination $savedLog
        $artifacts += [ordered]@{path=$savedLog;sha256=(Get-FileHash -LiteralPath $savedLog).Hash.ToLowerInvariant()}
    }
    $receipt = [ordered]@{
        schemaVersion=1;sessionId=$session
        startedAt=$started.ToString('o');finishedAt=[DateTime]::UtcNow.ToString('o')
        minecraftVersion='1.21.1';neoForgeVersion='21.1.84'
        processStartCount=1;worldLoadCount=1;scenarioCount=1
        failedCount=$(if($passed){0}else{1});cleanupFailureCount=$(if($passed){0}else{1})
        allScenariosPassed=$passed;requiredGameTestCount=$(if($match.Success){[int]$match.Groups[1].Value}else{0})
        requestedScenarios=@('all-required-gametests');observedScenarios=@('all-required-gametests')
        sourceManifest=$manifest;sourceManifestSha256=(Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant()
        scenarios=@([ordered]@{id='all-required-gametests';passed=$passed;cleanupPassed=$passed;startedAtTick=0;finishedAtTick=0;artifacts=$artifacts})
    }
    $receipt | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $harness -Encoding utf8
    Copy-Item -LiteralPath $harness -Destination (Join-Path $output "gametest-$session.json")
    if(-not $passed){$code=1}
} finally { Pop-Location }
exit $code
