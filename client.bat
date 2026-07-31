@echo off
cd /d "%~dp0.."
echo [LuaTweaker] Building classes, syncing Lua, and launching NeoForge Client...
call ./gradlew :neoforge-platform:runClient
