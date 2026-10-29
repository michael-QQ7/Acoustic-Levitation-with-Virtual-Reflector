@echo off
cd /d "%~dp0"
java -cp "dist\AcousticField3D.jar;jars\*" acousticfield3d.AcousticField3D %*
if errorlevel 1 pause
