$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Python = @(Get-Command python -CommandType Application -ErrorAction Stop)[0].Source
& $Python "$ProjectRoot\.minecraft-mod-testing\minecraft_mod_testing.py" --project $ProjectRoot @args
exit $LASTEXITCODE
