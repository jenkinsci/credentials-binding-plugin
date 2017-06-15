REM check existence of the keystore file
if not defined MY_KEYSTORE exit /B 1
if not exist %MY_KEYSTORE% exit /B 1

REM check the other variables
if not defined KEYSTORE_PASSWORD exit /B 1
if not defined KEYSTORE_ALIAS exit /B 1

REM keep location of the keystore file for the next step
echo %MY_KEYSTORE% > keystore-path

