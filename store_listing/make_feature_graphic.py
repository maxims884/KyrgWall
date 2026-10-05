"""Обложка для Google Play (feature graphic) 1024×500 на трёх языках.

Берёт три открытки прямо из Firestore, поэтому запускать с интернетом:
    python make_feature_graphic.py
"""
import io
import os

import requests
from PIL import Image, ImageDraw, ImageFilter

from make_screenshots import GOLD, HERE, background, font, rounded_mask, wrap

W, H = 1024, 500
TEXTS = {
    "ru": ("Кыргызстан обои и открытки", "Новые картинки каждый день"),
    "ky": ("Кыргызстан тушкагаздары жана открыткалар", "Күн сайын жаңы сүрөттөр"),
    "en": ("Kyrgyzstan Wallpapers & Cards", "New pictures every day"),
}
FIRESTORE = "https://firestore.googleapis.com/v1/projects/hd-wallpaper-b78a8/databases/(default)/documents/cards"
WANTED = ["nooruz", "kurman_ait", "birthday"]


def card_images():
    docs = requests.get(FIRESTORE, params={"pageSize": 100}, timeout=60).json().get("documents", [])
    images = []
    for occasion in WANTED:
        doc = next(d for d in docs if "-%s-" % occasion in d["name"])
        url = doc["fields"]["url"]["stringValue"]
        images.append(Image.open(io.BytesIO(requests.get(url, timeout=60).content)).convert("RGB"))
    return images


def make(lang, cards):
    canvas = background((W, H)).convert("RGBA")
    # Три открытки веером справа
    positions = [(585, 95, -8), (700, 65, 0), (815, 95, 8)]
    for card, (x, y, angle) in zip(cards, positions):
        c = card.resize((196, 294), Image.LANCZOS).convert("RGBA")
        c.putalpha(rounded_mask(c.size, 18))
        shadow = Image.new("RGBA", (c.width + 60, c.height + 60), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle((30, 40, 30 + c.width, 40 + c.height), 18, fill=(0, 0, 0, 160))
        shadow = shadow.filter(ImageFilter.GaussianBlur(14)).rotate(angle, expand=True, resample=Image.BICUBIC)
        rotated = c.rotate(angle, expand=True, resample=Image.BICUBIC)
        canvas.alpha_composite(shadow, (x - 30 - (shadow.width - c.width - 60) // 2, y - 30))
        canvas.alpha_composite(rotated, (x - (rotated.width - c.width) // 2, y - (rotated.height - c.height) // 2))

    draw = ImageDraw.Draw(canvas)
    title, subtitle = TEXTS[lang]
    size = 56
    while True:
        title_font = font("Montserrat[wght].ttf", size, b"ExtraBold")
        lines = wrap(draw, title, title_font, 470)
        if len(lines) <= 3 and all(draw.textlength(l, font=title_font) <= 470 for l in lines):
            break
        size -= 2
    y = (H - len(lines) * size * 1.12 - 90) / 2
    for line in lines:
        draw.text((56, y), line, font=title_font, fill="white")
        y += size * 1.12
    y += 22
    draw.line([(56, y), (150, y)], fill=GOLD, width=3)
    draw.polygon([(166, y - 8), (174, y), (166, y + 8), (158, y)], fill=GOLD)
    draw.line([(182, y), (276, y)], fill=GOLD, width=3)
    draw.text((56, y + 26), subtitle, font=font("Montserrat[wght].ttf", 30, b"Medium"), fill=(255, 255, 255, 220))

    out = os.path.join(HERE, "screenshots", lang)
    os.makedirs(out, exist_ok=True)
    canvas.convert("RGB").save(os.path.join(out, "feature_graphic.png"), optimize=True)
    print("готово", lang)


if __name__ == "__main__":
    cards = card_images()
    for lang in TEXTS:
        make(lang, cards)
