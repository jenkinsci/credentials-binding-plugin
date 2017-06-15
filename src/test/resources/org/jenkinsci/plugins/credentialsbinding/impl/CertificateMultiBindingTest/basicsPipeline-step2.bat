REM get path of the keystore file
if not exist keystore-path exit /B 1
set /p keystore_path=<keystore-path

REM check it has been deleted
if exist %keystore_path% exit /B 1
