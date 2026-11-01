#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec java --enable-native-access=ALL-UNNAMED -Djava.library.path=natives -cp "EndPoem.jar:lib/*" com.endpoem.Main "$@"
