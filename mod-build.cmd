@echo off
python "%~dp0.minecraft-mod-testing\minecraft_mod_testing.py" --project "%~dp0." %*
exit /b %errorlevel%
