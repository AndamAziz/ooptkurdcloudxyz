#!/usr/bin/env python3
"""Renders the EnglishMaster brand assets from one definition of the logo.

The logo is an open book with the sun rising behind it (learning as a new day) on the app's
indigo-to-violet gradient. The Android adaptive icon (core/designsystem/src/main/res/drawable/
ic_launcher_*.xml) uses the same shapes.

Outputs (docs/brand/):
  logo.svg                      vector master
  icon-512.png                  Google Play hi-res icon (512x512, square; Play applies the mask)
  feature-graphic-1024x500.png  Google Play feature graphic

Usage: python3 scripts/media/make_logo.py   (needs: pip install cairosvg)
"""
import math
from pathlib import Path

import cairosvg

OUT = Path(__file__).resolve().parents[2] / "docs/brand"
CX, CY = 54, 50
SUN = "#FFC845"
LEFT = "M52.6,79 C45,74.2 35.5,73 27,74.6 L27,50.6 C35.5,49 45,50.2 52.6,55 Z"
RIGHT = "M55.4,79 C63,74.2 72.5,73 81,74.6 L81,50.6 C72.5,49 63,50.2 55.4,55 Z"
LINES_L = ["M32,57.2 C38,56.6 43.5,57.4 48.5,59.6", "M32,62.6 C38,62 43.5,62.8 48.5,65", "M32,68 C38,67.4 43.5,68.2 48.5,70.4"]
LINES_R = [p.replace("M32", "M76").replace("C38", "C70").replace("43.5", "64.5").replace("48.5", "59.5") for p in LINES_L]
DEFS = (
    '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">'
    '<stop offset="0" stop-color="#3A4FC4"/><stop offset="1" stop-color="#7B4FD6"/></linearGradient>'
    '<radialGradient id="glow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFD56B" stop-opacity="0.55"/>'
    '<stop offset="1" stop-color="#FFD56B" stop-opacity="0"/></radialGradient></defs>'
)


def artwork() -> str:
    """Sun, rays and book on the 108-unit adaptive-icon canvas."""
    parts = [f'<circle cx="{CX}" cy="{CY}" r="28" fill="url(#glow)"/>', f'<circle cx="{CX}" cy="{CY}" r="11.5" fill="{SUN}"/>']
    for a in (150, 120, 90, 60, 30):
        t = math.radians(a)
        x1, y1 = CX + 15.5 * math.cos(t), CY - 15.5 * math.sin(t)
        x2, y2 = CX + 21 * math.cos(t), CY - 21 * math.sin(t)
        parts.append(f'<path d="M{x1:.2f},{y1:.2f} L{x2:.2f},{y2:.2f}" stroke="{SUN}" stroke-width="3.2" stroke-linecap="round"/>')
    parts.append(f'<path d="{LEFT}" fill="#FFFFFF"/><path d="{RIGHT}" fill="#F1EEFF"/>')
    parts += [f'<path d="{p}" stroke="#B9B3EA" stroke-width="1.4" fill="none" stroke-linecap="round"/>' for p in LINES_L + LINES_R]
    return "".join(parts)


def icon_svg(corner: float = 0) -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{DEFS}'
            f'<rect width="108" height="108" rx="{corner}" fill="url(#g)"/>{artwork()}</svg>')


def feature_graphic_svg() -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">{DEFS}'
        '<rect width="1024" height="500" fill="url(#g)"/>'
        '<circle cx="930" cy="-40" r="260" fill="#FFFFFF" fill-opacity="0.06"/>'
        '<circle cx="60" cy="560" r="220" fill="#FFFFFF" fill-opacity="0.05"/>'
        f'<g transform="translate(70,100) scale(2.78)">{artwork()}</g>'
        '<text x="400" y="225" font-family="Inter Display, Inter, DejaVu Sans" font-weight="700" font-size="76" fill="#FFFFFF">EnglishMaster</text>'
        '<text x="402" y="290" font-family="Inter, DejaVu Sans" font-size="31" fill="#FFFFFF" fill-opacity="0.92">Learn English step by step, A1 to C2</text>'
        '<text x="402" y="338" font-family="Inter, DejaVu Sans" font-size="25" fill="#FFE29A">Practice · Exams · Certificates · Kurdish &amp; English</text>'
        '</svg>'
    )


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "logo.svg").write_text(icon_svg(corner=24), encoding="utf-8")
    cairosvg.svg2png(bytestring=icon_svg().encode(), write_to=str(OUT / "icon-512.png"), output_width=512, output_height=512)
    cairosvg.svg2png(bytestring=feature_graphic_svg().encode(), write_to=str(OUT / "feature-graphic-1024x500.png"),
                     output_width=1024, output_height=500)
    print("wrote", ", ".join(sorted(p.name for p in OUT.iterdir())))


if __name__ == "__main__":
    main()
