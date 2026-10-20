#!/usr/bin/env python3
"""Generate the sample question media in content/media/samples/ (deterministic).

Pictures are drawn with Pillow; short videos are rendered frame by frame and encoded with
ffmpeg (H.264, yuv420p, no audio) so that Android's built-in player can stream them.
These are illustrative samples; teachers can upload real photos and videos from the app.

Usage: python3 scripts/media/generate_samples.py
"""
import math
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "content/media/samples"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
W, H = 800, 500
BG = (247, 247, 252)
INK = (27, 42, 94)


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)


def canvas(bg=BG):
    img = Image.new("RGB", (W, H), bg)
    return img, ImageDraw.Draw(img)


def centered(d, xy, text, size, fill=INK, bold=False):
    f = font(size, bold)
    box = d.textbbox((0, 0), text, font=f)
    d.text((xy[0] - (box[2] - box[0]) / 2, xy[1] - (box[3] - box[1]) / 2), text, font=f, fill=fill)


# ---------------------------------------------------------------- pictures
def clock(d, cx, cy, r, hour, minute):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill="white", outline=INK, width=8)
    for i in range(12):
        a = math.radians(i * 30 - 90)
        x1, y1 = cx + math.cos(a) * (r - 10), cy + math.sin(a) * (r - 10)
        x2, y2 = cx + math.cos(a) * (r - 30), cy + math.sin(a) * (r - 30)
        d.line((x1, y1, x2, y2), fill=INK, width=6 if i % 3 == 0 else 3)
        n = 12 if i == 0 else i
        centered(d, (cx + math.cos(a) * (r - 55), cy + math.sin(a) * (r - 55)), str(n), 26)
    ha = math.radians((hour % 12 + minute / 60) * 30 - 90)
    ma = math.radians(minute * 6 - 90)
    d.line((cx, cy, cx + math.cos(ha) * r * 0.5, cy + math.sin(ha) * r * 0.5), fill=INK, width=12)
    d.line((cx, cy, cx + math.cos(ma) * r * 0.8, cy + math.sin(ma) * r * 0.8), fill=(58, 79, 196), width=7)
    d.ellipse((cx - 10, cy - 10, cx + 10, cy + 10), fill=INK)


def clock_picture(name, hour, minute):
    img, d = canvas()
    clock(d, W // 2, H // 2, 200, hour, minute)
    img.save(OUT / name)


def apple(d, x, y):
    d.ellipse((x - 38, y - 34, x + 38, y + 40), fill=(214, 40, 40), outline=(140, 20, 20), width=3)
    d.line((x, y - 34, x + 6, y - 58), fill=(90, 60, 30), width=6)
    d.ellipse((x + 6, y - 62, x + 34, y - 44), fill=(60, 150, 60))


def banana(d, x, y):
    # A crescent: a yellow ellipse with a background-coloured ellipse cut out of its top.
    d.ellipse((x - 95, y - 60, x + 95, y + 45), fill=(245, 205, 40), outline=(190, 150, 20), width=3)
    d.ellipse((x - 105, y - 95, x + 105, y + 15), fill=BG)
    d.rectangle((x + 78, y - 22, x + 92, y - 6), fill=(110, 80, 30))


def fruit_picture():
    img, d = canvas()
    for x in (150, 300, 450):
        apple(d, x, 200)
    for x in (230, 520):
        banana(d, x, 380)
    img.save(OUT / "fruit_bowl.png")


def rain_picture():
    img, d = canvas((205, 214, 228))
    for cx, cy, r in ((300, 140, 80), (390, 110, 100), (490, 150, 75), (400, 170, 90)):
        d.ellipse((cx - r, cy - r * 0.7, cx + r, cy + r * 0.7), fill=(120, 130, 150))
    for i in range(40):
        x = 200 + (i * 37) % 420
        y = 240 + (i * 53) % 200
        d.line((x, y, x - 10, y + 28), fill=(40, 90, 200), width=4)
    # umbrella
    d.pieslice((560, 300, 740, 460), 180, 360, fill=(214, 40, 40))
    d.line((650, 380, 650, 470), fill=INK, width=6)
    d.arc((630, 450, 660, 480), 0, 180, fill=INK, width=6)
    img.save(OUT / "rainy_day.png")


def table_ball_picture():
    img, d = canvas()
    d.rectangle((180, 200, 620, 230), fill=(150, 100, 60))
    for x in (200, 580):
        d.rectangle((x, 230, x + 22, 420), fill=(120, 80, 45))
    d.line((100, 420, 700, 420), fill=(170, 170, 180), width=4)
    d.ellipse((350, 330, 440, 420), fill=(214, 40, 40), outline=(140, 20, 20), width=3)
    d.arc((350, 330, 440, 420), 200, 340, fill="white", width=6)
    # a box on the table, empty
    d.rectangle((450, 130, 560, 200), outline=INK, width=5, fill=(235, 210, 160))
    img.save(OUT / "ball_and_table.png")


def no_parking_sign():
    img, d = canvas()
    d.rectangle((385, 300, 415, 480), fill=(150, 150, 160))
    d.ellipse((250, 40, 550, 340), fill=(30, 90, 200), outline=(214, 40, 40), width=34)
    centered(d, (400, 190), "P", 150, fill="white", bold=True)
    d.line((300, 90, 500, 290), fill=(214, 40, 40), width=34)
    img.save(OUT / "no_parking.png")


def bar_chart(d, values, labels, title, top=None):
    centered(d, (W // 2, 40), title, 28, bold=True)
    top = top or max(values)
    base, left, right, height = 440, 110, 760, 330
    d.line((left, base, right, base), fill=INK, width=3)
    d.line((left, base, left, base - height - 10), fill=INK, width=3)
    for i in range(0, top + 1, 10 if top <= 60 else 100):
        y = base - height * i / top
        d.line((left - 6, y, left, y), fill=INK, width=2)
        d.text((left - 52, y - 10), str(i), font=font(18), fill=INK)
    step = (right - left) / len(values)
    for i, (v, lab) in enumerate(zip(values, labels)):
        x0 = left + i * step + step * 0.2
        x1 = left + (i + 1) * step - step * 0.2
        d.rectangle((x0, base - height * v / top, x1, base), fill=(58, 79, 196))
        centered(d, ((x0 + x1) / 2, base + 22), lab, 20)


def sales_chart():
    img, d = canvas()
    bar_chart(d, [40, 35, 20, 30, 45, 60], ["Jan", "Feb", "Mar", "Apr", "May", "Jun"],
              "Bicycles sold per month (2026)", top=60)
    img.save(OUT / "sales_chart.png")


def table(d, rows, col_x, top, row_h, header_fill=(58, 79, 196)):
    for r, row in enumerate(rows):
        y = top + r * row_h
        if r == 0:
            d.rectangle((40, y, W - 40, y + row_h), fill=header_fill)
        elif r % 2 == 0:
            d.rectangle((40, y, W - 40, y + row_h), fill=(232, 234, 246))
        for c, cell in enumerate(row):
            d.text((col_x[c], y + row_h / 2 - 13), cell, font=font(24, bold=r == 0), fill="white" if r == 0 else INK)
    d.rectangle((40, top, W - 40, top + len(rows) * row_h), outline=INK, width=2)


def timetable():
    img, d = canvas()
    centered(d, (W // 2, 45), "Bus timetable: Erbil -> Sulaymaniyah", 28, bold=True)
    table(d, [["Bus", "Leaves Erbil", "Arrives"], ["1", "07:00", "10:15"], ["2", "08:15", "10:45"],
              ["3", "12:30", "15:30"], ["4", "17:45", "20:45"]], [70, 260, 560], 100, 70)
    img.save(OUT / "bus_timetable.png")


def shop_hours():
    img, d = canvas((255, 250, 240))
    centered(d, (W // 2, 60), "CITY BOOKSHOP", 40, bold=True)
    centered(d, (W // 2, 115), "Opening hours", 28)
    table(d, [["Day", "Open", "Close"], ["Monday - Friday", "9:00", "17:00"], ["Saturday", "10:00", "14:00"],
              ["Sunday", "closed", ""]], [70, 430, 600], 160, 70, header_fill=(0, 137, 123))
    img.save(OUT / "shop_hours.png")


# ---------------------------------------------------------------- videos
def encode(frames_dir, name, fps):
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps), "-i", str(frames_dir / "f%04d.png"),
         "-c:v", "libx264", "-pix_fmt", "yuv420p", "-profile:v", "baseline", "-level", "3.0",
         "-movflags", "+faststart", "-crf", "28", str(OUT / name)],
        check=True,
    )


def video(name, seconds, fps, draw):
    tmp = Path(tempfile.mkdtemp())
    try:
        n = int(seconds * fps)
        for i in range(n):
            img, d = canvas()
            draw(d, i / (n - 1))
            img.save(tmp / f"f{i:04d}.png")
        encode(tmp, name, fps)
    finally:
        shutil.rmtree(tmp)


def clock_video():
    # 2:00 -> 2:45 over 6 seconds, then hold.
    def draw(d, t):
        m = min(45, round(45 * min(1.0, t / 0.8)))
        clock(d, W // 2, H // 2, 200, 2, m)
    video("clock_2_to_quarter_to_3.mp4", 7, 15, draw)


def balloons_video():
    colors = [(214, 40, 40), (30, 90, 200), (0, 150, 120), (240, 180, 0), (140, 60, 200)]

    def draw(d, t):
        shown = min(5, int(t / 0.12) + 1)
        for i in range(shown):
            x = 140 + i * 130
            y = 230
            if i == 2 and t > 0.7:  # the green balloon flies away
                y -= (t - 0.7) / 0.3 * 400
            d.line((x, y + 70, x, y + 230), fill=(90, 90, 90), width=2)
            d.ellipse((x - 50, y - 70, x + 50, y + 70), fill=colors[i])
    video("balloons.mp4", 7, 15, draw)


def day_video():
    scenes = [("7:00", "Sara wakes up."), ("7:30", "She has breakfast."), ("8:00", "She takes the bus to work."),
              ("13:00", "She has lunch with a friend."), ("18:00", "She goes to the gym.")]

    def draw(d, t):
        i = min(len(scenes) - 1, int(t * len(scenes)))
        time, text = scenes[i]
        clock(d, 220, 250, 140, int(time.split(":")[0]), int(time.split(":")[1]))
        centered(d, (560, 200), time, 54, bold=True)
        centered(d, (560, 290), text, 26)
    video("saras_day.mp4", 10, 10, draw)


def visitors_video():
    target = [200, 300, 400]

    def draw(d, t):
        g = min(1.0, t / 0.7)
        bar_chart(d, [max(1, int(v * g)) for v in target], ["2020", "2022", "2024"], "Museum visitors (thousands)", top=400)
    video("museum_visitors.mp4", 6, 15, draw)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    clock_picture("clock_three.png", 3, 0)
    clock_picture("clock_half_past_seven.png", 7, 30)
    fruit_picture()
    rain_picture()
    table_ball_picture()
    no_parking_sign()
    sales_chart()
    timetable()
    shop_hours()
    clock_video()
    balloons_video()
    day_video()
    visitors_video()
    for p in sorted(OUT.iterdir()):
        print(f"{p.relative_to(ROOT)}  {p.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
