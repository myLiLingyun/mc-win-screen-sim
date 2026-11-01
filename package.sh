#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
rm -rf dist
mkdir -p dist
cp EndPoem.jar dist/
cp -r lib natives assets assets-placeholder tools dist/
rm -rf dist/tools/__pycache__
cp run.sh run.bat NOTICE.md LICENSE LICENSE.CC-BY-NC-SA-4.0 THIRD-PARTY.md dist/
find dist -name README.md -delete
chmod 755 dist/run.sh
rm -f EndPoem-26.2-dist.zip
python3 - <<'PY'
import pathlib, zipfile
root = pathlib.Path('dist')
with zipfile.ZipFile('EndPoem-26.2-dist.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for p in sorted(root.rglob('*')):
        if p.is_file():
            z.write(p, p.relative_to(root))
PY
echo "packaged dist/ and EndPoem-26.2-dist.zip"

