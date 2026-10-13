[CmdletBinding()]
param(
    [string]$PackDirectory = '',
    [string]$OutputDirectory = '',
    [switch]$ReloadOnly,
    [int]$LockTimeoutSeconds = 0
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Run with PowerShell 7 (pwsh).' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtime = [IO.Path]::GetFullPath((Join-Path $root 'runs/visual-tests'))
$output = if ($OutputDirectory) { [IO.Path]::GetFullPath($OutputDirectory) }
    else { Join-Path $root "build/visual-tests/$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))" }
if (-not $output.StartsWith((Join-Path $root 'build/visual-tests') + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Visual output must remain inside the project visual-tests output root.'
}
$receipt = Join-Path $output 'client-receipt.json'
$ExpectedScenarios = 98 # 89 appearance scenarios and 9 actual resource-pack reloads.
if ($ReloadOnly) { $ExpectedScenarios = 10 }

if ($env:MINECRAFT_MOD_TEST_SINGLE_INSTANCE -ne '1') {
    & (Join-Path $root 'mod-build.ps1') ensure
    if ($LASTEXITCODE -ne 0) { throw 'Managed release gate failed' }
    $arguments = @('-NoProfile', '-NonInteractive', '-File', $PSCommandPath, '-OutputDirectory', $output)
    if ($PackDirectory) { $arguments += @('-PackDirectory', (Resolve-Path -LiteralPath $PackDirectory).Path) }
    if ($ReloadOnly) { $arguments += '-ReloadOnly' }
    & (Join-Path $root '.agents/skills/minecraft-mod-testing/scripts/invoke_single_session.ps1') `
        -ProjectRoot $root -Executable (Get-Command pwsh).Source -ArgumentList $arguments -RuntimeKind client `
        -RuntimeDirectory $runtime -HarnessReceiptPath $receipt -ExpectedScenarioCount $ExpectedScenarios `
        -LockTimeoutSeconds $LockTimeoutSeconds
    exit $LASTEXITCODE
}

# The managed parent already holds this runtime's lease and verified its process inventory.
$config = Join-Path $runtime 'config/fml.toml'
$options = Join-Path $runtime 'options.txt'
$packRoot = Join-Path $runtime 'resourcepacks'
$zip = Join-Path $packRoot 'appearance-under-test.zip'
New-Item -ItemType Directory -Force -Path $output, (Split-Path $config), $packRoot | Out-Null
$saved = @{}
foreach ($file in @($config, $options, $zip)) {
    $saved[$file] = if (Test-Path -LiteralPath $file) { [IO.File]::ReadAllBytes($file) } else { $null }
}
$oldJava = $env:JAVA_HOME
$oldPath = $env:PATH
$session = $env:MINECRAFT_MOD_TEST_SESSION_ID
$inputs = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -File) +
    @(Get-Item -LiteralPath (Join-Path $root 'build.gradle'), $PSCommandPath,
        (Join-Path $root '.minecraft-mod-testing/install.json'))
@($inputs | ForEach-Object {
    @{path=[IO.Path]::GetRelativePath($root, $_.FullName);sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}
}) | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'source-manifest.json') -Encoding utf8
$utf8 = [Text.UTF8Encoding]::new($false)
$code = 1
try {
    # Preserve existing saves; the harness names its fresh world with the session UUID.
    [IO.File]::WriteAllText($config, "earlyWindowControl=false`n", $utf8)
    $packs = '"vanilla","mod_resources","mod/neoforge","mod/companiondog","mod/companiondog_visualtest"'
    if ($PackDirectory) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        Remove-Item -LiteralPath $zip -ErrorAction SilentlyContinue
        [IO.Compression.ZipFile]::CreateFromDirectory((Resolve-Path -LiteralPath $PackDirectory).Path, $zip)
        $packs += ',"file/appearance-under-test.zip"'
    }
    [IO.File]::WriteAllText($options, ("resourcePacks:[$packs]`nfullscreen:false`nmaxFps:30`npauseOnLostFocus:false`n" +
        "guiScale:1`nrenderDistance:3`nsimulationDistance:5`nsoundCategory_master:0.0`nonboardAccessibility:false`n"), $utf8)
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    Push-Location $root
    try {
        $scope = @()
        if ($ReloadOnly) { $scope += '-PresourceReloadOnly' }
        & .\gradlew.bat runClient --no-daemon --console=plain -PvisualTest `
            "-PvisualTestReceipt=$receipt" "-PvisualTestOutput=$output" "-PminecraftModTestingRuntimeDirectory=$runtime" @scope
        $code = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $oldJava
    $env:PATH = $oldPath
    foreach ($file in @($saved.Keys)) {
        if ($null -eq $saved[$file]) { Remove-Item -LiteralPath $file -ErrorAction SilentlyContinue }
        else { [IO.File]::WriteAllBytes($file, $saved[$file]) }
    }
    $prefix = "companiondog-reload-$session-"
    foreach ($fixture in @(Get-ChildItem -LiteralPath $packRoot | Where-Object { $_.Name.StartsWith($prefix) })) {
        $target = [IO.Path]::GetFullPath($fixture.FullName)
        if ([IO.Path]::GetDirectoryName($target) -ne $packRoot -or -not $fixture.Name.StartsWith($prefix)) {
            throw 'Fixture cleanup escaped its declared resource-pack directory.'
        }
        Remove-Item -LiteralPath $target -Recurse -Force
    }
    $restored = @($saved.Keys | ForEach-Object {
        $exists = Test-Path -LiteralPath $_
        $matched = if ($null -eq $saved[$_]) { -not $exists }
            else { $exists -and [Convert]::ToHexString([IO.File]::ReadAllBytes($_)) -eq [Convert]::ToHexString($saved[$_]) }
        @{path=$_;restored=$matched}
    })
    $restored | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'restoration.json') -Encoding utf8
    if (@($restored | Where-Object { -not $_.restored }).Count -gt 0) { throw 'Test configuration restoration failed.' }
}
Write-Output "Visual frames, reload observations and receipts: $output"
exit $code
