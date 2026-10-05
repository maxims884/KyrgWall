"""Рисует открытку: фото на фоне, затемнение, поздравление и пожелание."""
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

FONTS = os.path.join(os.path.dirname(__file__), "fonts")
SIZE = (1080, 1620)  # 2:3 — хорошо смотрится и в сетке приложения, и в WhatsApp
GOLD = (255, 213, 79)
WHITE = (255, 255, 255)


def _font(name, size, variation=None):
    font = ImageFont.truetype(os.path.join(FONTS, name), size)
    if variation:
        font.set_variation_by_name(variation)
    return font


def _wrap(draw, text, font, max_width):
    words, lines, line = text.split(), [], ""
    for word in words:
        test = (line + " " + word).strip()
        if draw.textlength(test, font=font) <= max_width or not line:
            line = test
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def _fit(draw, text, name, size, min_size, max_width, max_lines, variation=None):
    """Самый крупный шрифт, при котором текст влезает в max_lines строк"""
    while True:
        font = _font(name, size, variation)
        lines = _wrap(draw, text, font, max_width)
        if (len(lines) <= max_lines and all(draw.textlength(l, font=font) <= max_width for l in lines)) \
                or size <= min_size:
            return font, lines
        size -= 4


def _gradient(size, from_bottom):
    """Затемнение под текстом, чтобы он читался на любом фото"""
    w, h = size
    mask = Image.new("L", (1, h))
    start = int(h * 0.38)
    for y in range(h):
        t = (y - start) / (h - start) if from_bottom else ((h - start) - y) / (h - start)
        mask.putpixel((0, y), int(215 * max(0.0, min(1.0, t)) ** 1.3))
    mask = mask.resize(size)
    black = Image.new("RGBA", size, (0, 0, 0, 255))
    black.putalpha(mask)
    return black


def is_good_background(background_bytes):
    """Отсеивает почти чёрные и однотонные фото: открытка на них выглядит пустой"""
    import io
    from PIL import ImageStat
    gray = Image.open(io.BytesIO(background_bytes)).convert("L")
    gray.thumbnail((128, 128))
    stat = ImageStat.Stat(gray)
    return stat.mean[0] >= 55 and stat.stddev[0] >= 25


def render(background_bytes, title, subtitle, brand, seed=None):
    rnd = random.Random(seed)
    import io
    bg = Image.open(io.BytesIO(background_bytes)).convert("RGB")
    bg = ImageOps.fit(bg, SIZE, Image.LANCZOS)
    card = bg.convert("RGBA")

    at_bottom = rnd.random() < 0.7
    card = Image.alpha_composite(card, _gradient(SIZE, at_bottom))

    text_layer = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(text_layer)
    w, h = SIZE
    max_width = int(w * 0.86)
    title_color = GOLD if rnd.random() < 0.5 else WHITE

    title_font, title_lines = _fit(draw, title, "Lobster-Regular.ttf", 132, 64, max_width, 3)
    sub_font, sub_lines = _fit(draw, subtitle, "Montserrat[wght].ttf", 50, 34, max_width, 3, b"SemiBold")
    title_h = int(title_font.size * 1.18)
    sub_h = int(sub_font.size * 1.35)
    block = len(title_lines) * title_h + 60 + len(sub_lines) * sub_h
    top = h - block - int(h * 0.09) if at_bottom else int(h * 0.08)

    y = top
    for line in title_lines:
        draw.text((w / 2, y), line, font=title_font, fill=title_color, anchor="ma")
        y += title_h
    # Разделитель: линия с ромбом посередине
    y += 22
    cx = w / 2
    draw.line([(cx - 150, y), (cx - 22, y)], fill=GOLD, width=3)
    draw.line([(cx + 22, y), (cx + 150, y)], fill=GOLD, width=3)
    draw.polygon([(cx, y - 11), (cx + 11, y), (cx, y + 11), (cx - 11, y)], fill=GOLD)
    y += 38
    for line in sub_lines:
        draw.text((w / 2, y), line, font=sub_font, fill=WHITE, anchor="ma")
        y += sub_h

    # Мягкая тень под всем текстом
    shadow = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    shadow.putalpha(text_layer.getchannel("A").filter(ImageFilter.GaussianBlur(8)).point(lambda a: int(a * 0.8)))
    card = Image.alpha_composite(card, shadow)
    card = Image.alpha_composite(card, text_layer)

    # Небольшая подпись приложения: каждая пересланная открытка — реклама
    brand_layer = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    ImageDraw.Draw(brand_layer).text(
        (w - 28, h - 26), brand, font=_font("Montserrat[wght].ttf", 26, b"Medium"),
        fill=(255, 255, 255, 150), anchor="rd")
    card = Image.alpha_composite(card, brand_layer)
    return card.convert("RGB")
