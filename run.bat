@echo off
cd /d "%~dp0"
java --enable-native-access=ALL-UNNAMED -Djava.library.path=natives -cp "EndPoem.jar;lib\*" com.endpoem.Main %*
