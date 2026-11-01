#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
find src -name '*.java' > sources.txt
javac -encoding UTF-8 --release 17 -d out -cp "lib/*" @sources.txt
jar --create --file EndPoem.jar --manifest manifest.txt -C out .
rm -f sources.txt
echo "built EndPoem.jar"
