#!/usr/bin/env python3
"""Renders every EnglishMaster brand asset from one definition of the logo.

The logo is an open book with the sun rising behind it (learning as a new day) on a deep
indigo-to-violet gradient. It is drawn once on a 1024-unit canvas; every output scales it.

Outputs:
  docs/brand/logo.svg                      vector master (rounded square)
  docs/brand/icon-512.png                  Google Play hi-res icon (512x512, full-bleed; Play applies the mask)
  docs/brand/ios-app-icon-1024.png         App Store icon (1024x1024, no transparency, no corners)
  docs/brand/feature-graphic-1024x500.png  Google Play feature graphic
  android/core/designsystem/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
                                           Android adaptive icon layers (also used by the splash screen and AppLogo)

Usage: python3 scripts/media/make_logo.py   (needs: pip install cairosvg pillow)
"""
import io
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/brand"
DRAWABLE = ROOT / "android/core/designsystem/src/main/res/drawable"

BG_START, BG_END = "#2E3FB8", "#6D3FD0"
SUN_IN, SUN_OUT, RAY, HALO = "#FFE08A", "#FFB627", "#FFC845", "#FFD56B"
SUN = (512, 470, 128)  # cx, cy, r
HALO_R = 300
RAYS = ["M512,270 L512,215", "M372,330 L333,291", "M652,330 L691,291", "M312,470 L257,470", "M712,470 L767,470"]
RAY_W = 34
# (path, colour): the two pages, then their lower edges, which give the book its thickness.
BOOK = [
    ("M500,800 C420,735 300,715 200,730 L200,520 C300,505 420,525 500,590 Z", "#FFFFFF"),
    ("M524,800 C604,735 724,715 824,730 L824,520 C724,505 604,525 524,590 Z", "#EDE9FF"),
    ("M500,800 C420,735 300,715 200,730 L200,760 C300,745 420,765 500,830 Z", "#C9C0F5"),
    ("M524,800 C604,735 724,715 824,730 L824,760 C724,745 604,765 524,830 Z", "#B7ADEE"),
]
# Adaptive icons show the middle 72 of 108dp and launchers may mask to a 66dp circle:
# the artwork (about 640 units tall) is scaled to sit inside that circle.
ADAPTIVE_SCALE = 0.082


def svg_defs() -> str:
    cx, cy, _ = SUN
    return (
        '<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
        f'<stop offset="0" stop-color="{BG_START}"/><stop offset="1" stop-color="{BG_END}"/></linearGradient>'
        f'<radialGradient id="sun" cx="{cx}" cy="{cy}" r="{SUN[2]}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{SUN_IN}"/><stop offset="1" stop-color="{SUN_OUT}"/></radialGradient>'
        f'<radialGradient id="halo" cx="{cx}" cy="{cy}" r="{HALO_R}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{HALO}" stop-opacity="0.45"/><stop offset="1" stop-color="{HALO}" stop-opacity="0"/>'
        '</radialGradient></defs>'
    )


def artwork_svg() -> str:
    cx, cy, r = SUN
    parts = [f'<circle cx="{cx}" cy="{cy}" r="{HALO_R}" fill="url(#halo)"/>',
             f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#sun)"/>']
    parts += [f'<path d="{d}" stroke="{RAY}" stroke-width="{RAY_W}" stroke-linecap="round"/>' for d in RAYS]
    parts += [f'<path d="{d}" fill="{c}"/>' for d, c in BOOK]
    return "".join(parts)


def icon_svg(corner: float = 0) -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{svg_defs()}'
            f'<rect width="1024" height="1024" rx="{corner}" fill="url(#bg)"/>{artwork_svg()}</svg>')


def feature_graphic_svg() -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">{svg_defs()}'
        '<rect width="1024" height="500" fill="url(#bg)"/>'
        '<circle cx="930" cy="-40" r="260" fill="#FFFFFF" fill-opacity="0.06"/>'
        '<circle cx="60" cy="560" r="220" fill="#FFFFFF" fill-opacity="0.05"/>'
        f'<g transform="translate(40,20) scale(0.43)">{artwork_svg()}</g>'
        '<text x="470" y="215" font-family="Inter Display, Inter, DejaVu Sans" font-weight="700" font-size="72" fill="#FFFFFF">EnglishMaster</text>'
        '<text x="472" y="278" font-family="Inter, DejaVu Sans" font-size="29" fill="#FFFFFF" fill-opacity="0.92">Learn English step by step, A1 to C2</text>'
        '<text x="472" y="326" font-family="Inter, DejaVu Sans" font-size="23" fill="#FFE29A">Practice · Reviews · Exams · Certificates</text>'
        '</svg>'
    )


def png(svg: str, path: Path, w: int, h: int, opaque: bool = False) -> None:
    data = cairosvg.svg2png(bytestring=svg.encode(), output_width=w, output_height=h)
    img = Image.open(io.BytesIO(data))
    img = img.convert("RGB") if opaque else img.convert("RGBA")
    img.save(path, optimize=True)


# --- Android vector drawables ---------------------------------------------------------------

def argb(hex_colour: str, alpha: float = 1.0) -> str:
    return f"#{round(alpha * 255):02X}{hex_colour.lstrip('#').upper()}"


def circle_path(cx: float, cy: float, r: float) -> str:
    return f"M{cx},{cy} m-{r},0 a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 -{2 * r},0"


def vector(body: str, comment: str, aapt: bool = False) -> str:
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment}\n     Generated by scripts/media/make_logo.py; edit the script, not this file. -->\n"
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        f"{body}</vector>\n"
    )


def scaled_group(inner: str) -> str:
    s = ADAPTIVE_SCALE
    t = round(54 - 512 * s, 3)
    return (f'    <group android:scaleX="{s}" android:scaleY="{s}" android:translateX="{t}" android:translateY="{t}">\n'
            f"{inner}    </group>\n")


def android_background() -> str:
    body = (
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">\n'
        f'                <item android:offset="0" android:color="{argb(BG_START)}" />\n'
        f'                <item android:offset="1" android:color="{argb(BG_END)}" />\n'
        '            </gradient>\n        </aapt:attr>\n    </path>\n'
    )
    return vector(body, "Brand gradient behind the logo (deep indigo to violet).", aapt=True)


def radial(path: str, r: float, stops: list[tuple[float, str]]) -> str:
    cx, cy, _ = SUN
    items = "".join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in stops)
    return (
        f'        <path android:pathData="{path}">\n'
        '            <aapt:attr name="android:fillColor">\n'
        f'                <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}">\n'
        f"{items}"
        '                </gradient>\n            </aapt:attr>\n        </path>\n'
    )


def android_foreground() -> str:
    cx, cy, r = SUN
    inner = radial(circle_path(cx, cy, HALO_R), HALO_R, [(0, argb(HALO, 0.45)), (1, argb(HALO, 0))])
    inner += radial(circle_path(cx, cy, r), r, [(0, argb(SUN_IN)), (1, argb(SUN_OUT))])
    inner += "".join(f'        <path android:pathData="{d}" android:strokeColor="{RAY}" android:strokeWidth="{RAY_W}" '
                     'android:strokeLineCap="round" />\n' for d in RAYS)
    inner += "".join(f'        <path android:fillColor="{c}" android:pathData="{d}" />\n' for d, c in BOOK)
    return vector(scaled_group(inner),
                  "EnglishMaster logo: an open book with the sun rising behind it (learning as a new day).\n"
                  "     108dp adaptive canvas; the artwork stays inside the 66dp safe zone.", aapt=True)


def android_monochrome() -> str:
    cx, cy, r = SUN
    inner = f'        <path android:fillColor="#000000" android:pathData="{circle_path(cx, cy, r)}" />\n'
    inner += "".join(f'        <path android:pathData="{d}" android:strokeColor="#000000" android:strokeWidth="{RAY_W}" '
                     'android:strokeLineCap="round" />\n' for d in RAYS)
    inner += "".join(f'        <path android:fillColor="#000000" android:pathData="{d}" />\n' for d, _ in BOOK)
    return vector(scaled_group(inner), "Single-colour logo for themed icons (Android 13+): the system tints it.")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "logo.svg").write_text(icon_svg(corner=230), encoding="utf-8")
    png(icon_svg(), OUT / "icon-512.png", 512, 512)
    png(icon_svg(), OUT / "ios-app-icon-1024.png", 1024, 1024, opaque=True)
    png(feature_graphic_svg(), OUT / "feature-graphic-1024x500.png", 1024, 500, opaque=True)
    (DRAWABLE / "ic_launcher_background.xml").write_text(android_background(), encoding="utf-8")
    (DRAWABLE / "ic_launcher_foreground.xml").write_text(android_foreground(), encoding="utf-8")
    (DRAWABLE / "ic_launcher_monochrome.xml").write_text(android_monochrome(), encoding="utf-8")
    print("wrote", ", ".join(sorted(p.name for p in OUT.iterdir())), "and the launcher layers")


if __name__ == "__main__":
    main()
