#!/usr/bin/env python3
"""Renders every EnglishMaster brand asset from the emblem in brand_art.py.

The emblem is navy and gold: Big Ben rising from an open book, a graduation cap, the Union flag
and a speech bubble inside a gold ring. App icons use it without the name (text is unreadable
at icon size and the stores show the name anyway); the full logo adds "English Master".

Outputs:
  docs/brand/logo.svg                      app icon, rounded square (vector master)
  docs/brand/logo-full.svg, logo-full.png  full logo with the name (1024x1024)
  docs/brand/icon-512.png                  Google Play hi-res icon (512x512, full-bleed; Play applies the mask)
  docs/brand/ios-app-icon-1024.png         App Store icon (1024x1024, no transparency, no corners)
  docs/brand/feature-graphic-1024x500.png  Google Play feature graphic
  android/core/designsystem/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
                                           Android adaptive icon layers (also used by the splash screen and AppLogo)

Usage: python3 scripts/media/make_logo.py   (needs: pip install cairosvg pillow fonttools)
Fonts: scripts/media/fonts (Playfair Display, SIL Open Font License).
"""
import io
from pathlib import Path

import cairosvg
from PIL import Image

import brand_art as art

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/brand"
DRAWABLE = ROOT / "android/core/designsystem/src/main/res/drawable"

# The 1024 artwork fills the 72dp an adaptive icon shows of its 108dp canvas; the gold ring then
# sits inside the 66dp circle that every launcher mask keeps.
VISIBLE_SCALE = 72 / 1024
VISIBLE_OFFSET = 18


def png(svg: str, path: Path, w: int, h: int, opaque: bool = False) -> None:
    img = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode(), output_width=w, output_height=h)))
    (img.convert("RGB") if opaque else img.convert("RGBA")).save(path, optimize=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "logo.svg").write_text(art.icon_svg(rounded=230), encoding="utf-8")
    full = art.emblem_svg()
    (OUT / "logo-full.svg").write_text(full, encoding="utf-8")
    png(full, OUT / "logo-full.png", 1024, 1024, opaque=True)
    png(art.icon_svg(), OUT / "icon-512.png", 512, 512)
    png(art.icon_svg(), OUT / "ios-app-icon-1024.png", 1024, 1024, opaque=True)
    png(art.feature_graphic_svg(), OUT / "feature-graphic-1024x500.png", 1024, 500, opaque=True)

    (DRAWABLE / "ic_launcher_background.xml").write_text(
        art.vector_xml(art.background(), "Navy background of the launcher icon.", scale=108 / 1024), encoding="utf-8")
    (DRAWABLE / "ic_launcher_foreground.xml").write_text(
        art.vector_xml(art.artwork(), "EnglishMaster emblem: Big Ben rising from an open book, with a graduation cap,\n"
                       "     the Union flag and a speech bubble in a gold ring. Stays inside the 66dp safe zone.",
                       scale=VISIBLE_SCALE, translate=VISIBLE_OFFSET), encoding="utf-8")
    (DRAWABLE / "ic_launcher_monochrome.xml").write_text(
        art.vector_xml(art.monochrome(), "Single-colour emblem for themed icons (Android 13+): the system tints it.",
                       mono=True, scale=VISIBLE_SCALE, translate=VISIBLE_OFFSET), encoding="utf-8")
    print("wrote", ", ".join(sorted(p.name for p in OUT.iterdir() if p.is_file())), "and the launcher layers")


if __name__ == "__main__":
    main()
