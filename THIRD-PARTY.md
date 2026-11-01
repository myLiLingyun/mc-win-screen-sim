# Third-party components

This project's own code is GPL-3.0-only. The following third-party components are
included as separate libraries/binaries and remain under their own licenses.

## LWJGL 3.4.1

- Files: `lib/lwjgl*.jar`, `natives/*.so`
- License: BSD-3-Clause
- Project: https://www.lwjgl.org/
- Copyright: LWJGL contributors

The native libraries are the Linux x86_64 builds published by LWJGL. If you
replace them for another platform, keep the corresponding LWJGL license.

## Gson 2.14.0

- File: `lib/gson-2.14.0.jar`
- License: Apache License 2.0
- Project: https://github.com/google/gson
- Copyright: Google Inc. and contributors

## Placeholder generation

The placeholder PNGs and ASCII atlas were generated for this project with
Python Pillow. Pillow is not redistributed. The generated bitmap atlas uses
the CC0 Aileron font bundled with Pillow; no font software is included.
