# NOTICE

End Poem Player is a fan-made project, not affiliated with Mojang Studios,
Microsoft or NetEase.

## Bundled content

- `assets/end_poem_en.txt` — English text of the End Poem, public domain.
- `assets-placeholder/` — generated placeholder textures, GLSL shaders and an
  ASCII bitmap font, created for this project and licensed GPL-3.0.
  The placeholder bitmap font was generated with Pillow using the CC0 Aileron
  font; no font software is redistributed.
- `src/` — GPL-3.0 code.
- `docs/` and this file — CC BY-NC-SA 4.0 documentation.

## Not bundled

This repository does **not** contain Mojang/Microsoft Minecraft assets,
Mojangles or Unifont font files, the NetEase Chinese translation, the four
Classical Chinese translations, Minecraft shaders, textures or the credits
music. Users may place their own licensed assets in `assets/`; the program
reads them locally and does not redistribute them.

If you supply Unifont yourself, its own dual license (OFL-1.1, or GPL-2.0+ with
the GNU font embedding exception) applies to those files. If you supply a
Minecraft asset tree, it remains subject to Mojang's terms. If you supply the
NetEase Chinese text or a wiki translation, its own terms apply; the four
Classical Chinese translations on 礦藝大典 are CC BY-NC-SA 3.0.

## Third-party libraries

- LWJGL 3.4.1 — BSD-3-Clause — https://www.lwjgl.org/
- Gson 2.14.0 — Apache-2.0 — https://github.com/google/gson

The bundled LWJGL native libraries are the Linux x86_64 builds distributed by
LWJGL. See `THIRD-PARTY.md` for details.
