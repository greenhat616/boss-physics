"""Generates the Workshop preview, the overview card and the captioned screenshots for Boss Physics.

Input: real game screenshots in docs/workshop/screenshots/ (taken by the autotest, see docs/workshop/PUBLISHING.md).
Output: src/main/resources/preview.png (640x640, the Workshop cover; the game only shows the upload button for a jar
that contains it), docs/workshop/images/00-overview.png (pixel-art feature card, same layout as the other mods' cards)
and docs/workshop/images/01..04 (1280x720, a caption bar over each screenshot).

Usage: python tools/make_workshop_images.py .
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(sys.argv[1])
FONTS = Path("C:/Windows/Fonts")
SHOTS = ROOT / "docs/workshop/screenshots"

CREAM = (246, 232, 196)
OUTLINE = (34, 24, 20)
ACCENT = (255, 196, 84)
TEXT_DIM = (210, 220, 228)

CAPTIONS = [
    # Numbers from the autotest run the screenshots come from (client.log "autotest fight" lines).
    ("01-crystaldragon.png", "crystaldragon-shards.png", "墙挡住 boss 的弹体", "Walls stop boss projectiles",
     "Crystal Dragon 的碎片打在墙上：这场测试 29 发命中墙，0 发穿墙", "Crystal Dragon's shards hit the walls: 29 wall hits, none through, in this test"),
    ("02-cryoqueen.png", "cryoqueen-volley.png", "但 boss 会把墙打穿", "But bosses break the walls",
     "每次命中都削减墙的耐久，一面墙至少要挨 4 下", "Every hit chips the wall; one projectile takes at most a quarter of it"),
    ("03-breach.png", "cryoqueen-breach.png", "躲墙后撑不了太久", "Cover holds, for a while",
     "Cryo Queen 的冰锥在石墙上打开了缺口（这场测试 16.7 秒）", "Cryo Queen's volleys open gaps in the stone box (16.7 s in this test)"),
    ("04-vulture.png", "ancientvulture-breach.png", "17 个 boss，各有工具等级", "17 bosses, each with a tool tier",
     "后期 boss 才能拆高级墙；定居点、boss 场地和地牢默认不受影响", "Only late bosses break strong walls; settlements, arenas and dungeons stay safe"),
]


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


GRASS = (78, 128, 52)
GRASS_SPECK = (64, 108, 44)
GRASS_LIGHT = (96, 148, 64)
STONE = (120, 120, 132)
STONE_DARK = (82, 82, 94)
STONE_TOP = (58, 58, 68)
BOLT = (232, 72, 64)
BOLT_CORE = (255, 200, 150)
SPARK = (255, 236, 160)


def grass(w, h, seed):
    rng = random.Random(seed)
    img = Image.new("RGB", (w, h), GRASS)
    d = ImageDraw.Draw(img)
    for _ in range(w * h // 30):
        d.point((rng.randrange(w), rng.randrange(h)), fill=GRASS_SPECK)
    for _ in range(w * h // 160):
        x, y = rng.randrange(w), rng.randrange(h)
        d.line([(x, y), (x + 1, y - 2)], fill=GRASS_LIGHT)
    return img


TILE = 10


def wall_tile(d, x, y, broken=False):
    """One stone wall tile seen from above: dark top, lit front face. Broken: rubble only."""
    t = TILE
    if broken:
        for dx, dy in ((1, 6), (5, 7), (7, 4), (2, 2), (6, 1)):
            d.rectangle([x + dx, y + dy, x + dx + 1, y + dy + 1], fill=STONE)
        return
    d.rectangle([x, y, x + t - 1, y + t - 1], fill=OUTLINE)
    d.rectangle([x + 1, y + 1, x + t - 2, y + t - 5], fill=STONE_TOP)
    d.rectangle([x + 1, y + t - 4, x + t - 2, y + t - 2], fill=STONE)
    d.line([(x + 1, y + t - 3), (x + t - 2, y + t - 3)], fill=STONE_DARK)
    d.point((x + t // 2, y + t - 2), fill=STONE_DARK)


def cracked_tile(d, x, y):
    wall_tile(d, x, y)
    crack = (196, 196, 206)
    d.line([(x + 2, y + 1), (x + 4, y + 3), (x + 3, y + 5), (x + 5, y + 7)], fill=crack)
    d.line([(x + 4, y + 3), (x + 7, y + 2)], fill=crack)


def player(d, x, y, shirt=(64, 120, 196)):
    """A tiny settler; (x, y) is the feet."""
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


def boss(d, cx, cy):
    """An Evils-Protector-like floating eye: dark shell, red iris."""
    d.ellipse([cx - 15, cy - 15, cx + 15, cy + 15], fill=OUTLINE)
    d.ellipse([cx - 13, cy - 13, cx + 13, cy + 13], fill=(92, 40, 110))
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=(150, 70, 160))
    d.ellipse([cx - 7, cy - 6, cx + 5, cy + 6], fill=CREAM)
    d.ellipse([cx - 5, cy - 4, cx + 2, cy + 3], fill=BOLT)
    d.rectangle([cx - 3, cy - 2, cx - 1, cy + 1], fill=OUTLINE)
    for i, (ox, oy) in enumerate(((-18, -6), (17, -9), (-14, 14), (15, 12))):
        d.rectangle([cx + ox - 1, cy + oy - 1, cx + ox + 1, cy + oy + 1], fill=(150, 70, 160))


def bolt(d, x, y, tail):
    """A boss bolt flying left; (x, y) is its head."""
    for i in range(tail):
        c = BOLT if i < tail - 3 else (150, 50, 50)
        d.line([(x + 4 + i, y - (1 if i < tail // 2 else 0)), (x + 4 + i, y + (1 if i < tail // 2 else 0))], fill=c)
    d.rectangle([x - 2, y - 3, x + 4, y + 3], fill=OUTLINE)
    d.rectangle([x - 1, y - 2, x + 3, y + 2], fill=BOLT)
    d.rectangle([x, y - 1, x + 1, y], fill=BOLT_CORE)


def spark(d, x, y):
    for dx, dy in ((0, -4), (0, 4), (4, 0), (-3, -3), (3, 3), (3, -3), (-3, 3), (5, -2), (5, 2)):
        d.rectangle([x + dx, y + dy, x + dx + 1, y + dy + 1], fill=SPARK)
    d.rectangle([x, y - 1, x + 1, y + 1], fill=(255, 255, 255))


def overview_scene(d):
    """Low resolution (320x180) scene for the right half of the card."""
    # A stone box, 7x7 tiles, left of centre of the art area.
    bx, by, n = 182, 44, 7
    for i in range(n):
        for j in range(n):
            if i not in (0, n - 1) and j not in (0, n - 1):
                continue
            x, y = bx + i * TILE, by + j * TILE
            if i == n - 1 and j == 2:
                cracked_tile(d, x, y)          # being hit
            elif i == n - 1 and j == 4:
                wall_tile(d, x, y, broken=True)  # already broken through
            else:
                wall_tile(d, x, y)
    player(d, bx + 33, by + 44)
    ex = bx + n * TILE
    # Bolts: one stopped at the cracked tile, one flying in, one heading for the gap it broke.
    spark(d, ex + 1, by + 2 * TILE + 5)
    bolt(d, ex + 12, by + 2 * TILE + 5, 10)
    bolt(d, ex + 22, by + 4 * TILE + 5, 10)
    boss(d, 300, by + 35)


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


def overview(out):
    """1280x720 card like the other mods': text panel on the left, pixel art on the right."""
    w, h = 1280, 720
    low = grass(w // 4, h // 4, seed=17)
    overview_scene(ImageDraw.Draw(low))
    img = low.resize((w, h), Image.NEAREST).convert("RGBA")
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rounded_rectangle([60, 70, 700, 640], radius=18, fill=(10, 16, 28, 215), outline=(246, 232, 196, 90), width=2)
    img = Image.alpha_composite(img, overlay)
    d = ImageDraw.Draw(img)
    d.text((100, 110), "墙挡 boss，boss 拆墙", font=font("msyhbd.ttc", 52), fill=CREAM)
    d.text((102, 182), "Walls stop bosses, bosses break walls", font=font("segoeuib.ttf", 26), fill=ACCENT)
    lines = [
        ("boss 的直射弹体撞墙即停", "Boss projectiles stop at the first wall"),
        ("每次命中削墙，至少 4 下打穿", "Every hit chips the wall; at least 4 hits to break it"),
        ("召唤物先破门再拆墙，boss 按等级拆墙", "Summons break doors, then walls; bosses by tool tier"),
        ("定居点、boss 场地和地牢默认不受影响", "Settlements, arenas and dungeons stay safe"),
    ]
    y = 250
    for zh, en in lines:
        d.rectangle([102, y + 14, 114, y + 26], fill=ACCENT)
        d.text((134, y - 4), zh, font=font("msyh.ttc", 28), fill=CREAM)
        d.text((134, y + 36), en, font=font("segoeuib.ttf", 19), fill=TEXT_DIM)
        y += 92
    d.text((w - 40, h - 30), "Boss Physics · Necesse", font=font("segoeuib.ttf", 20), fill=TEXT_DIM, anchor="rm")
    img.convert("RGB").save(out, optimize=True)


def preview(out):
    """Square crop of the Crystal Dragon shot (walled box, player, shards on the walls, dragon) with the title."""
    shot = Image.open(SHOTS / "crystaldragon-shards.png").convert("RGB")
    crop = shot.crop((440, 0, 1160, 720)).resize((640, 640), Image.LANCZOS).convert("RGBA")
    shade = Image.new("RGBA", crop.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    sd.rectangle([0, 0, 640, 150], fill=(10, 16, 28, 150))
    img = Image.alpha_composite(crop, shade)
    d = ImageDraw.Draw(img)
    d.text((320, 78), "BOSS PHYSICS", font=font("ariblk.ttf", 64), fill=CREAM, anchor="mm", stroke_width=6,
           stroke_fill=OUTLINE)
    d.text((320, 600), "walls stop boss attacks", font=font("segoeuib.ttf", 30), fill=ACCENT, anchor="mm",
           stroke_width=3, stroke_fill=OUTLINE)
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
