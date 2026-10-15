"""Generates the Workshop images for Boss Physics.

- src/main/resources/preview.png: 640x640 cover (the game only shows the upload button for a jar that contains it).
- docs/workshop/images/00-overview.png: 1280x720 feature card.
  Both are pixel art in the same style as the other mods in this repo (Coop Pause, Team Map Sync, Net Reliability):
  a banded night sky with stars and a grass strip, drawn at low resolution and upscaled with nearest-neighbour; text
  is drawn afterwards at full resolution.
- docs/workshop/images/01..04: real game screenshots from docs/workshop/screenshots/ (taken by the autotest, see
  docs/workshop/PUBLISHING.md) with a caption bar.

Usage: python tools/make_workshop_images.py .
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(sys.argv[1])
FONTS = Path("C:/Windows/Fonts")
SHOTS = ROOT / "docs/workshop/screenshots"

SKY_TOP = (22, 34, 58)
SKY_BOTTOM = (52, 78, 104)
GRASS = (86, 140, 62)
GRASS_DARK = (62, 106, 46)
DIRT = (110, 78, 52)
DIRT_DARK = (84, 58, 40)
CREAM = (246, 232, 196)
OUTLINE = (34, 24, 20)
SHADOW = (14, 20, 32)
ACCENT = (255, 196, 84)
TEXT_DIM = (196, 210, 222)
STONE = (128, 128, 140)
STONE_LIGHT = (160, 160, 172)
STONE_DARK = (90, 90, 104)
BOLT = (232, 72, 64)
BOLT_CORE = (255, 210, 160)
SPARK = (255, 236, 160)
EYE_SHELL = (92, 40, 110)
EYE_RING = (150, 70, 160)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scene(w, h, seed=7, ground=0.22):
    """Banded night sky, stars and a grass strip, at pixel-art resolution (same as Coop Pause)."""
    rng = random.Random(seed)
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    ground_y = int(h * (1 - ground))
    bands = 8
    for y in range(ground_y):
        t = min(bands - 1, int(y / ground_y * bands)) / (bands - 1)
        d.line([(0, y), (w, y)], fill=lerp(SKY_TOP, SKY_BOTTOM, t))
    for _ in range(w * h // 260):
        x, y = rng.randrange(w), rng.randrange(int(ground_y * 0.8))
        img.putpixel((x, y), lerp(SKY_BOTTOM, (255, 255, 240), rng.uniform(0.5, 1)))
    d.rectangle([0, ground_y, w, h], fill=DIRT)
    for x in range(w):
        lip = ground_y + rng.choice([0, 0, 1])
        d.line([(x, ground_y - rng.choice([0, 0, 1])), (x, lip + 3)], fill=GRASS)
        d.point((x, lip + 4), fill=GRASS_DARK)
    for _ in range(w * (h - ground_y) // 18):
        d.point((rng.randrange(w), rng.randrange(ground_y + 6, h)), fill=DIRT_DARK)
    return img, ground_y


def player(d, x, y, shirt):
    """A tiny Necesse-ish settler (same sprite as the other mods); (x, y) is the feet."""
    def px(a, b, c, e, f):
        d.rectangle([x + a, y + b, x + c - 1, y + e - 1], fill=f)
    px(-4, -9, 5, -3, OUTLINE)
    px(-4, -16, 4, -8, OUTLINE)
    px(-3, -14, 3, -8, (232, 186, 150))
    px(-3, -15, 3, -13, (92, 60, 40))
    px(-1, -11, 0, -10, OUTLINE)
    px(1, -11, 2, -10, OUTLINE)
    px(-4, -8, 4, -3, shirt)
    px(-3, -3, -1, 0, (60, 60, 90))
    px(1, -3, 3, 0, (60, 60, 90))


def wall(d, x, ground_y, cols, rows, brick_w=8, brick_h=5):
    """A stone brick wall standing on the ground, side view, running bond. Returns (right edge x, top y)."""
    top = ground_y - rows * brick_h
    right = x + cols * brick_w
    d.rectangle([x - 1, top - 1, right, ground_y], fill=OUTLINE)
    for r in range(rows):
        y0 = top + r * brick_h
        bx = x - (brick_w // 2 if r % 2 else 0)
        while bx < right:
            x0, x1 = max(x, bx), min(right, bx + brick_w)
            if x1 - x0 > 1:
                d.rectangle([x0, y0, x1 - 2, y0 + brick_h - 2], fill=STONE)
                d.line([(x0, y0), (x1 - 2, y0)], fill=STONE_LIGHT)
            bx += brick_w
    return right, top


def chip(d, right, y, w, h, sky_at):
    """Bite a w x h notch out of the wall's right side at height y, outlined."""
    for yy in range(y, y + h + 1):
        d.line([(right - w, yy), (right, yy)], fill=sky_at(yy))
    d.line([(right - w - 1, y), (right - w - 1, y + h)], fill=OUTLINE)
    d.line([(right - w, y + h + 1), (right, y + h + 1)], fill=OUTLINE)


def crack(d, x, y):
    for dx, dy in ((0, 0), (-1, 1), (-1, 2), (-2, 3), (0, 3), (-2, 4), (1, 1), (2, 1)):
        d.point((x + dx, y + dy), fill=OUTLINE)


def rubble(d, x, ground_y):
    for dx, dy, s in ((0, -2, 2), (3, -1, 1), (5, -2, 2), (8, -1, 1)):
        d.rectangle([x + dx - 1, ground_y + dy - 1, x + dx + s + 1, ground_y + dy + s + 1], fill=OUTLINE)
        d.rectangle([x + dx, ground_y + dy, x + dx + s, ground_y + dy + s], fill=STONE_DARK)


def boss(d, cx, cy, r=14):
    """A floating eye (Evils Protector-like) with a drop shadow."""
    d.ellipse([cx - r + 2, cy - r + 2, cx + r + 2, cy + r + 2], fill=SHADOW)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=OUTLINE)
    d.ellipse([cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2], fill=EYE_SHELL)
    d.ellipse([cx - r + 5, cy - r + 5, cx + r - 5, cy + r - 5], fill=EYE_RING)
    e = r // 2
    d.ellipse([cx - e - 2, cy - e, cx + e - 2, cy + e], fill=CREAM)
    d.ellipse([cx - e, cy - e // 2 - 1, cx - 1, cy + e // 2], fill=BOLT)
    d.rectangle([cx - e + 2, cy - 1, cx - e + 3, cy], fill=OUTLINE)


def bolt(d, x, y, tail=8):
    """A boss bolt flying left; (x, y) is its head."""
    for i in range(tail):
        c = BOLT if i < tail - 3 else (150, 56, 56)
        half = 1 if i < tail // 2 else 0
        d.line([(x + 3 + i, y - half), (x + 3 + i, y + half)], fill=c)
    d.rectangle([x - 2, y - 3, x + 3, y + 2], fill=OUTLINE)
    d.rectangle([x - 1, y - 2, x + 2, y + 1], fill=BOLT)
    d.rectangle([x, y - 1, x + 1, y], fill=BOLT_CORE)


def spark(d, x, y):
    for dx, dy in ((1, -4), (1, 4), (4, 0), (3, -3), (3, 3), (6, -2), (6, 2)):
        d.rectangle([x + dx, y + dy, x + dx + 1, y + dy + 1], fill=SPARK)
    d.rectangle([x, y - 1, x + 2, y + 1], fill=(255, 255, 255))


def fight(d, img, x, ground_y):
    """Player behind a stone wall, boss to the right firing at it; x is the wall's left edge. The wall's right side
    is chipped and cracked where bolts hit it."""
    sky_at = lambda y: img.getpixel((1, y))
    right, top = wall(d, x, ground_y, cols=2, rows=7)
    chip(d, right, top - 1, 7, 4, sky_at)        # the top corner knocked off
    chip(d, right, top + 15, 4, 4, sky_at)       # a bite lower down
    crack(d, right - 4, top + 8)
    crack(d, right - 8, top + 24)
    rubble(d, right + 2, ground_y)
    player(d, x - 12, ground_y, (64, 120, 196))
    spark(d, right + 1, top + 11)                # this bolt just hit the wall
    bolt(d, right + 18, top + 20)                # the next one on its way
    boss(d, right + 48, top + 8)


def preview(out):
    w = h = 160
    img, ground_y = scene(w, h, seed=7)
    fight(ImageDraw.Draw(img), img, 56, ground_y)
    img = img.resize((640, 640), Image.NEAREST)
    d = ImageDraw.Draw(img)
    d.text((320, 92), "BOSS PHYSICS", font=font("ariblk.ttf", 64), fill=CREAM, anchor="mm", stroke_width=6,
           stroke_fill=OUTLINE)
    img.save(out, optimize=True)


def overview(out):
    """1280x720 card like the other mods': text panel on the left, pixel art on the right."""
    w, h = 1280, 720
    low, ground_y = scene(w // 4, h // 4, seed=11, ground=0.15)  # ground below the text panel, as in Coop Pause
    fight(ImageDraw.Draw(low), low, 222, ground_y)
    img = low.resize((w, h), Image.NEAREST).convert("RGBA")
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rounded_rectangle([60, 70, 760, 610], radius=18, fill=(10, 16, 28, 215), outline=(246, 232, 196, 90), width=2)
    img = Image.alpha_composite(img, overlay)
    d = ImageDraw.Draw(img)
    d.text((100, 110), "墙挡 boss，boss 拆墙", font=font("msyhbd.ttc", 52), fill=CREAM)
    d.text((102, 182), "Walls stop bosses, and bosses break walls", font=font("segoeuib.ttf", 26), fill=ACCENT)
    lines = [
        ("boss 的直射弹体撞墙即停", "Boss projectiles stop at the first wall"),
        ("每次命中都削墙，至少 4 下打穿", "Every hit chips the wall; at least 4 hits to break it"),
        ("召唤物破门拆墙，boss 按等级拆墙", "Summons break doors and walls; bosses by tool tier"),
        ("定居点、boss 场地和地牢不受影响", "Settlements, arenas and dungeons stay safe"),
    ]
    y = 250
    for zh, en in lines:
        d.rectangle([102, y + 14, 114, y + 26], fill=ACCENT)
        d.text((134, y - 4), zh, font=font("msyh.ttc", 28), fill=CREAM)
        d.text((134, y + 36), en, font=font("segoeuib.ttf", 19), fill=TEXT_DIM)
        y += 86
    d.text((w - 40, h - 30), "Boss Physics · Necesse", font=font("segoeuib.ttf", 20), fill=TEXT_DIM, anchor="rm")
    img.convert("RGB").save(out, optimize=True)


# Screenshot captions. Numbers come from the autotest run the screenshots were taken in (client.log "autotest
# fight" lines); a caption must only claim what the shot shows.
CAPTIONS = [
    ("01-crystaldragon.png", "crystaldragon-shards.png", "墙挡住 boss 的弹体", "Walls stop boss projectiles",
     "Crystal Dragon 的碎片打在墙上：这场测试 29 发命中墙，0 发穿墙", "Crystal Dragon's shards hit the walls: 29 wall hits, none through, in this test"),
    ("02-cryoqueen.png", "cryoqueen-volley.png", "但 boss 会把墙打穿", "But bosses break the walls",
     "每次命中都削减墙的耐久，一面墙至少要挨 4 下", "Every hit chips the wall; one projectile takes at most a quarter of it"),
    ("03-breach.png", "cryoqueen-breach.png", "躲墙后撑不了太久", "Cover holds, for a while",
     "Cryo Queen 的冰锥在石墙上打开了缺口（这场测试 16.7 秒）", "Cryo Queen's volleys open gaps in the stone box (16.7 s in this test)"),
    ("04-vulture.png", "ancientvulture-breach.png", "17 个 boss，各有工具等级", "17 bosses, each with a tool tier",
     "后期 boss 才能拆高级墙；定居点、boss 场地和地牢默认不受影响", "Only late bosses break strong walls; settlements, arenas and dungeons stay safe"),
]


def captioned(out, shot, title_zh, title_en, line_zh, line_en):
    img = Image.open(SHOTS / shot).convert("RGB").resize((1280, 720), Image.LANCZOS).convert("RGBA")
    w, h = img.size
    band = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(band)
    bd.rectangle([0, h - 150, w, h], fill=(10, 16, 28, 205))
    img = Image.alpha_composite(img, band)
    d = ImageDraw.Draw(img)
    d.text((40, h - 136), title_zh, font=font("msyhbd.ttc", 38), fill=CREAM)
    zh_width = d.textlength(title_zh, font=font("msyhbd.ttc", 38))
    d.text((40 + zh_width + 24, h - 124), title_en, font=font("segoeuib.ttf", 26), fill=ACCENT)
    d.text((40, h - 76), line_zh, font=font("msyh.ttc", 24), fill=CREAM)
    d.text((40, h - 42), line_en, font=font("segoeuib.ttf", 19), fill=TEXT_DIM)
    d.text((w - 30, h - 24), "Boss Physics · Necesse", font=font("segoeuib.ttf", 18), fill=TEXT_DIM, anchor="rm")
    img.convert("RGB").save(out, optimize=True)


def main():
    res = ROOT / "src/main/resources"
    out = ROOT / "docs/workshop/images"
    res.mkdir(parents=True, exist_ok=True)
    out.mkdir(parents=True, exist_ok=True)
    preview(res / "preview.png")
    overview(out / "00-overview.png")
    for name, shot, *text in CAPTIONS:
        captioned(out / name, shot, *text)
    for p in [res / "preview.png", *sorted(out.glob("*.png"))]:
        print(p, Image.open(p).size, p.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
