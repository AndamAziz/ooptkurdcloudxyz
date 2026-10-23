"""The EnglishMaster emblem, drawn once and written out as SVG or as an Android vector drawable.

Navy and gold: Big Ben rising from an open book, a graduation cap, the Union flag and a speech
bubble inside a gold ring; the full emblem adds the name in Playfair Display. Everything is drawn
on a 1024-unit canvas with a small shape model (paths, gradients, groups with clips), because
Android vector drawables support only that much.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from pathlib import Path

FONTS = Path(__file__).resolve().parent / "fonts"

# --- Shape model ----------------------------------------------------------------------------


@dataclass
class Linear:
    x1: float
    y1: float
    x2: float
    y2: float
    stops: list  # (offset, "#RRGGBB", opacity)


@dataclass
class Radial:
    cx: float
    cy: float
    r: float
    stops: list


@dataclass
class P:
    d: str
    fill: object = None  # "#RRGGBB", Linear, Radial or None
    stroke: str | None = None
    width: float = 0
    alpha: float = 1.0
    cap: str = "round"


@dataclass
class G:
    children: list
    tx: float = 0
    ty: float = 0
    scale: float = 1
    rotate: float = 0  # degrees, about (px, py)
    px: float = 0
    py: float = 0
    clip: str | None = None
    items: list = field(default_factory=list)


def circle(cx, cy, r):
    return f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2 * r:g},0 a{r:g},{r:g} 0 1,0 {-2 * r:g},0 Z"


def rect(x, y, w, h):
    return f"M{x:g},{y:g} h{w:g} v{h:g} h{-w:g} Z"


# --- Palette --------------------------------------------------------------------------------

NAVY_IN, NAVY_MID, NAVY_OUT = "#1D2F63", "#0D1838", "#050A1A"
CREAM, CREAM_SHADE = "#FBF3DC", "#E3D2A6"
FLAG_BLUE, FLAG_RED = "#012169", "#C8102E"


def gold(y1, y2, x=0):
    """Polished gold, light at the top and deep at the bottom of the span y1..y2."""
    return Linear(x, y1, x, y2, [(0, "#FFF4C2", 1), (0.3, "#F6CD62", 1), (0.62, "#D79F32", 1), (1, "#8C5A14", 1)])


def gold_h(x1, x2, y=0):
    return Linear(x1, y, x2, y, [(0, "#9A6418", 1), (0.35, "#F2C557", 1), (0.55, "#FFF0B8", 1), (0.75, "#E3AE3E", 1), (1, "#8C5A14", 1)])


# --- Artwork --------------------------------------------------------------------------------


def background():
    return [P(rect(0, 0, 1024, 1024), Radial(512, 430, 760, [(0, NAVY_IN, 1), (0.55, NAVY_MID, 1), (1, NAVY_OUT, 1)]))]


def glow():
    return [P(circle(512, 470, 420), Radial(512, 470, 420, [(0, "#F6CD62", 0.20), (0.6, "#F6CD62", 0.06), (1, "#F6CD62", 0)]))]


def ring():
    return [
        P(circle(512, 512, 404), None, "#000000", 18, 0.35),
        P(circle(512, 512, 400), None, gold_h(112, 912, 512), 14),
        P(circle(512, 512, 381), None, "#F6CD62", 3, 0.45),
    ]


def big_ben():
    x = 512
    g = gold(120, 700)
    dark = "#0B1430"
    parts = [
        # spire and finial
        P(f"M{x - 4},{150} L{x},{96} L{x + 4},{150} Z", g),
        P(circle(x, 100, 7), "#FFF0B8"),
        # roof
        P(f"M{x - 58},258 L{x - 40},214 L{x - 14},150 L{x + 14},150 L{x + 40},214 L{x + 58},258 Z", g),
        P(f"M{x - 14},150 L{x - 4},258 L{x + 4},258 L{x + 14},150 Z", "#8C5A14", alpha=0.35),
        # corner pinnacles
        P(f"M{x - 70},270 L{x - 64},226 L{x - 58},270 Z", g),
        P(f"M{x + 58},270 L{x + 64},226 L{x + 70},270 Z", g),
        # belfry with arches
        P(rect(x - 62, 256, 124, 50), g),
        *[P(f"M{ax - 8},300 v-24 a8,8 0 0,1 16,0 v24 Z", dark, alpha=0.75) for ax in (x - 36, x - 12, x + 12, x + 36)],
        # clock stage
        P(rect(x - 72, 304, 144, 116), g),
        P(rect(x - 72, 304, 144, 8), "#FFF4C2", alpha=0.6),
        P(circle(x, 362, 49), "#8C5A14"),
        P(circle(x, 362, 44), CREAM),
        P(circle(x, 362, 44), None, "#B98326", 3),
        *[P(f"M{x + 37 * math.sin(math.radians(a)):.1f},{362 - 37 * math.cos(math.radians(a)):.1f} "
            f"L{x + 41 * math.sin(math.radians(a)):.1f},{362 - 41 * math.cos(math.radians(a)):.1f}", None, dark, 4)
          for a in range(0, 360, 30)],
        P(f"M{x},362 L{x},330", None, dark, 5),
        P(f"M{x},362 L{x + 22},372", None, dark, 5),
        P(circle(x, 362, 5), dark),
        # shaft
        P(rect(x - 56, 420, 112, 260), g),
        P(rect(x - 64, 418, 128, 12), "#FFF4C2", alpha=0.55),
        *[P(rect(sx - 4, 446, 8, 220), dark, alpha=0.32) for sx in (x - 30, x, x + 30)],
        *[P(rect(x - 56, sy, 112, 4), "#8C5A14", alpha=0.45) for sy in (500, 580)],
        # right side in shadow gives the tower depth
        P(rect(x + 20, 420, 36, 260), "#5C3A0C", alpha=0.25),
    ]
    return parts


def flag():
    """Union flag waving from a gold pole, drawn on a 0..60 x 0..30 field."""
    wave = "M0,0 C14,-4 30,4 44,-1 C51,-3.5 56,-2 60,-1 L60,29 C56,28 51,26.5 44,29 C30,34 14,26 0,30 Z"
    field_ = [
        P(rect(-2, -6, 64, 42), FLAG_BLUE),
        P("M-2,-1 L62,31", None, "#FFFFFF", 6, cap="butt"),
        P("M-2,31 L62,-1", None, "#FFFFFF", 6, cap="butt"),
        P("M-2,-1 L62,31", None, FLAG_RED, 2, cap="butt"),
        P("M-2,31 L62,-1", None, FLAG_RED, 2, cap="butt"),
        P(rect(-2, 10, 64, 10), "#FFFFFF"),
        P(rect(25, -6, 10, 42), "#FFFFFF"),
        P(rect(-2, 12, 64, 6), FLAG_RED),
        P(rect(27, -6, 6, 42), FLAG_RED),
        # folds
        P(rect(-2, -6, 64, 42), Linear(0, 0, 60, 0, [(0, "#000000", 0.25), (0.22, "#FFFFFF", 0.18), (0.45, "#000000", 0.28),
                                                     (0.7, "#FFFFFF", 0.16), (1, "#000000", 0.3)])),
    ]
    return [
        P("M672,214 L672,600", None, gold_h(664, 680), 9),
        P(circle(672, 208, 10), "#FFF0B8"),
        G(field_, tx=676, ty=222, scale=3.3, rotate=4, clip=wave),
    ]


def cap():
    """Graduation cap tilted on the left, its tassel hanging over the brim."""
    navy, edge = "#152248", "#0A1230"
    parts = [
        P("M58,40 L162,40 L158,92 C130,104 90,104 62,92 Z", Linear(0, 40, 0, 104, [(0, "#24366E", 1), (1, edge, 1)])),
        P("M58,40 C90,52 130,52 162,40", None, gold_h(58, 162, 0), 5),
        P("M0,30 L110,0 L220,30 L110,62 Z", Linear(0, 0, 220, 62, [(0, "#3A54A0", 1), (0.5, "#1E2F66", 1), (1, edge, 1)])),
        P("M0,30 L110,0 L220,30 L110,62 Z", None, gold_h(0, 220, 0), 4),
        P("M110,30 L208,40 L208,104", None, "#F6CD62", 4),
        P("M200,100 L216,100 L224,150 L192,150 Z", gold(100, 150)),
        P(circle(110, 30, 8), "#F6CD62"),
    ]
    return [G(parts, tx=150, ty=250, scale=1.18, rotate=-14, px=110, py=40)]


def book():
    """Open book: navy cover with a gold edge and cream pages fanning from the spine."""
    def pages(sign):
        def px(v):  # mirror around the spine
            return 512 + sign * (v - 512)
        layers = []
        for i, (top, col) in enumerate([(612, CREAM_SHADE), (600, "#F1E5C2"), (588, CREAM)]):
            d = (f"M{px(512)},{top + 92} C{px(450)},{top + 40} {px(340)},{top + 12} {px(212 + 6 * i)},{top + 22} "
                 f"L{px(196 + 6 * i)},{top + 112} C{px(330)},{top + 98} {px(440)},{top + 120} {px(512)},{top + 162} Z")
            layers.append(P(d, col))
        lines = [P(f"M{px(470)},{y + 20} C{px(420)},{y - 8} {px(340)},{y - 20} {px(262)},{y - 14}", None, "#C9B27A", 3, 0.7)
                 for y in (640, 664, 688)]
        return layers + lines

    cover = (
        "M512,792 C440,742 320,724 160,738 L176,700 C330,690 450,708 512,750 "
        "C574,708 694,690 848,700 L864,738 C704,724 584,742 512,792 Z"
    )
    return [
        P(cover, Linear(0, 690, 0, 792, [(0, "#22356E", 1), (1, "#0A1230", 1)])),
        P(cover, None, gold_h(160, 864, 0), 6),
        *pages(-1), *pages(1),
        P("M512,680 L512,752", None, "#B98326", 4, 0.8),
    ]


def bubble():
    parts = [
        P("M0,40 a46,40 0 1,1 92,0 a46,40 0 0,1 -46,40 c-10,0 -18,-2 -26,-6 L-6,86 L6,64 C2,58 0,50 0,40 Z", gold(0, 86)),
        P("M8,40 a38,32 0 1,1 76,0 a38,32 0 0,1 -76,0 Z", NAVY_MID),
        *[P(circle(cx, 40, 7), CREAM) for cx in (28, 46, 64)],
    ]
    return [G(parts, tx=204, ty=470)]


def artwork(with_bubble=True):
    scene = flag() + big_ben() + cap() + book() + (bubble() if with_bubble else [])
    return glow() + ring() + [G(scene, ty=36)]


def monochrome():
    """Silhouette for themed icons: the ring, tower, flag, cap and book (no glow or speech bubble)."""
    scene = flag() + big_ben() + cap() + book()
    return [ring()[1], G(scene, ty=36)]


# --- Text -----------------------------------------------------------------------------------


def text_path(text: str, font: str, size: float, x: float, y: float, anchor: str = "middle", tracking: float = 0) -> str:
    """The text's outlines as SVG path data (so the logo needs no font to display)."""
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    from fontTools.ttLib import TTFont

    f = TTFont(FONTS / font)
    gs = f.getGlyphSet()
    cmap = f.getBestCmap()
    scale = size / f["head"].unitsPerEm
    width = sum(gs[cmap[ord(c)]].width for c in text) * scale + tracking * (len(text) - 1)
    pen_x = x - (width / 2 if anchor == "middle" else 0)
    pen = SVGPathPen(gs)
    for c in text:
        glyph = gs[cmap[ord(c)]]
        glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, pen_x, y)))
        pen_x += glyph.width * scale + tracking
    return pen.getCommands()


def name_block(cx=512, y1=588, y2=696, size=124):
    english = text_path("English", "PlayfairDisplay-ExtraBold.ttf", size, cx, y1)
    master = text_path("Master", "PlayfairDisplay-ExtraBold.ttf", size, cx, y2)
    parts = []
    for d, base in ((english, y1), (master, y2)):
        parts.append(G([P(d, "#02050F", alpha=0.6)], tx=4, ty=7))
        parts.append(P(d, None, "#3A2405", 10))
        parts.append(P(d, gold(base - size * 0.72, base + 6)))
        parts.append(P(d, None, "#FFF4C2", 1.5, 0.5))
    return parts


# --- Output: SVG ----------------------------------------------------------------------------


class SvgWriter:
    def __init__(self):
        self.defs: list[str] = []

    def paint(self, p) -> str:
        if p is None:
            return "none"
        if isinstance(p, str):
            return p
        gid = f"g{len(self.defs)}"
        stops = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in p.stops)
        if isinstance(p, Linear):
            self.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{p.x1}" y1="{p.y1}" '
                             f'x2="{p.x2}" y2="{p.y2}">{stops}</linearGradient>')
        else:
            self.defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{p.cx}" cy="{p.cy}" '
                             f'r="{p.r}">{stops}</radialGradient>')
        return f"url(#{gid})"

    def node(self, n) -> str:
        if isinstance(n, G):
            t = f"translate({n.tx},{n.ty}) rotate({n.rotate},{n.px * n.scale},{n.py * n.scale}) scale({n.scale})"
            body = "".join(self.node(c) for c in n.children)
            if n.clip:
                cid = f"c{len(self.defs)}"
                self.defs.append(f'<clipPath id="{cid}"><path d="{n.clip}"/></clipPath>')
                body = f'<g clip-path="url(#{cid})">{body}</g>'
            return f'<g transform="{t}">{body}</g>'
        attrs = f'd="{n.d}" fill="{self.paint(n.fill)}"'
        if n.stroke:
            attrs += f' stroke="{self.paint(n.stroke)}" stroke-width="{n.width}" stroke-linecap="{n.cap}" stroke-linejoin="round"'
        if n.alpha != 1:
            attrs += f' opacity="{n.alpha}"'
        return f"<path {attrs}/>"

    def document(self, nodes, w=1024, h=1024, extra="") -> str:
        body = "".join(self.node(n) for n in nodes)
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
                f'<defs>{"".join(self.defs)}</defs>{body}{extra}</svg>')


def icon_svg(rounded: float = 0) -> str:
    w = SvgWriter()
    nodes = background() + artwork()
    if rounded:
        return w.document([G(nodes, clip=f"M{rounded},0 H{1024 - rounded} A{rounded},{rounded} 0 0,1 1024,{rounded} V{1024 - rounded} "
                                          f"A{rounded},{rounded} 0 0,1 {1024 - rounded},1024 H{rounded} A{rounded},{rounded} 0 0,1 0,{1024 - rounded} "
                                          f"V{rounded} A{rounded},{rounded} 0 0,1 {rounded},0 Z")])
    return w.document(nodes)


def emblem_svg() -> str:
    """The full logo with the name, for the store listing, the website and documents."""
    w = SvgWriter()
    return w.document(background() + artwork() + name_block())


def feature_graphic_svg() -> str:
    w = SvgWriter()
    bg = [P(rect(0, 0, 1024, 500), Radial(300, 250, 820, [(0, NAVY_IN, 1), (0.55, NAVY_MID, 1), (1, NAVY_OUT, 1)]))]
    emblem = G(artwork(), tx=44, ty=22, scale=0.445)
    title_e = text_path("EnglishMaster", "PlayfairDisplay-ExtraBold.ttf", 70, 500, 222, anchor="start")
    title = [G([P(title_e, "#02050F", alpha=0.6)], tx=3, ty=5), P(title_e, None, "#3A2405", 6), P(title_e, gold(170, 226))]
    tag = text_path("Learn English step by step, A1 to C2", "Inter.ttf", 30, 502, 288, anchor="start") if (FONTS / "Inter.ttf").exists() else None
    nodes = bg + [emblem] + title
    extra = ""
    if tag is None:
        extra = ('<text x="502" y="288" font-family="Inter, DejaVu Sans" font-size="27" fill="#FBF3DC">Learn English step by step, A1 to C2</text>'
                 '<text x="502" y="338" font-family="Inter, DejaVu Sans" font-size="22" fill="#F6CD62">Practice · Reviews · Exams · Certificates</text>')
    return w.document(nodes, 1024, 500, extra)


# --- Output: Android vector drawable --------------------------------------------------------


def _argb(c: str, a: float = 1) -> str:
    return f"#{round(a * 255):02X}{c.lstrip('#').upper()}"


class VectorWriter:
    def __init__(self, mono: bool = False):
        self.mono = mono

    def gradient(self, attr: str, g, alpha: float, ind: str) -> str:
        if isinstance(g, Linear):
            head = f'android:type="linear" android:startX="{g.x1}" android:startY="{g.y1}" android:endX="{g.x2}" android:endY="{g.y2}"'
        else:
            head = f'android:type="radial" android:centerX="{g.cx}" android:centerY="{g.cy}" android:gradientRadius="{g.r}"'
        items = "".join(f'{ind}        <item android:offset="{o}" android:color="{_argb(c, a * alpha)}" />\n' for o, c, a in g.stops)
        return (f'{ind}    <aapt:attr name="{attr}">\n{ind}      <gradient {head}>\n{items}{ind}      </gradient>\n'
                f'{ind}    </aapt:attr>\n')

    def node(self, n, ind="    ") -> str:
        if isinstance(n, G):
            attrs = (f'android:translateX="{n.tx}" android:translateY="{n.ty}" android:scaleX="{n.scale}" android:scaleY="{n.scale}" '
                     f'android:rotation="{n.rotate}" android:pivotX="{n.px}" android:pivotY="{n.py}"')
            inner = "".join(self.node(c, ind + "    ") for c in n.children)
            clip = f'{ind}    <clip-path android:pathData="{n.clip}" />\n' if n.clip else ""
            return f"{ind}<group {attrs}>\n{clip}{inner}{ind}</group>\n"
        attrs = [f'android:pathData="{n.d}"']
        inner = ""
        for kind, paint in (("fillColor", n.fill), ("strokeColor", n.stroke)):
            if paint is None:
                continue
            if self.mono:
                attrs.append(f'android:{kind}="#FF000000"')
            elif isinstance(paint, str):
                attrs.append(f'android:{kind}="{_argb(paint, n.alpha)}"')
            else:
                inner += self.gradient(f"android:{kind}", paint, n.alpha, ind)
        if n.stroke:
            attrs.append(f'android:strokeWidth="{n.width}" android:strokeLineCap="{n.cap}" android:strokeLineJoin="round"')
        if not inner:
            return f"{ind}<path {' '.join(attrs)} />\n"
        return f"{ind}<path {' '.join(attrs)}>\n{inner}{ind}</path>\n"


def vector_xml(nodes, comment: str, mono: bool = False, scale: float = 1, translate: float = 0) -> str:
    body = "".join(VectorWriter(mono).node(n, "        ") for n in nodes)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment}\n     Generated by scripts/media/make_logo.py; edit the script, not this file. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        f'    <group android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{translate}" android:translateY="{translate}">\n'
        f"{body}    </group>\n</vector>\n"
    )
