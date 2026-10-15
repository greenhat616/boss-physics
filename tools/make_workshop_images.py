"""Generates the Workshop preview and captioned screenshots for Boss Physics.

Input: real game screenshots in docs/workshop/screenshots/ (taken by the autotest, see docs/workshop/PUBLISHING.md).
Output: src/main/resources/preview.png (640x640, the Workshop cover; the game only shows the upload button for a jar
that contains it) and docs/workshop/images/*.png (1280x720, a caption bar over each screenshot).

Usage: python tools/make_workshop_images.py .
"""
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
    for name, shot, *text in CAPTIONS:
        captioned(out / name, shot, *text)
    for p in [res / "preview.png", *sorted(out.glob("*.png"))]:
        print(p, Image.open(p).size, p.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
