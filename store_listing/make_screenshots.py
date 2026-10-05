"""Собирает скриншоты для Google Play из снимков экрана приложения.

    python make_screenshots.py <папка со снимками> <язык ru|ky|en>

Снимки называются feed.png, viewer.png, cards.png, editor.png, settings.png, feed_light.png
(экран телефона без рамки). Результат: screenshots/<язык>/01.png … 06.png, 1080×1920.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "content_bot", "fonts")
W, H = 1080, 1920
GOLD = (255, 213, 79)

CAPTIONS = {
    "ru": [
        ("feed", "Кыргызстан в вашем телефоне", "Новые обои и открытки каждый день"),
        ("viewer", "Обои в высоком качестве", "На главный экран, экран блокировки или оба"),
        ("cards", "Открытки для WhatsApp", "К Айту, Нооруз, Жума и дню рождения"),
        ("editor", "Открытка с именем", "Впишите имя и отправьте в одно касание"),
        ("settings", "Каждый день — новые обои", "Автосмена и напоминания о праздниках"),
        ("feed_light", "Светлая и тёмная тема", "На кыргызском, русском и английском"),
    ],
    "ky": [
        ("feed", "Кыргызстан телефонуңузда", "Күн сайын жаңы тушкагаздар жана открыткалар"),
        ("viewer", "Сапаттуу тушкагаздар", "Башкы экранга, кулпу экранына же экөөнө тең"),
        ("cards", "WhatsApp үчүн открыткалар", "Айт, Нооруз, Жума жана туулган күнгө"),
        ("editor", "Ысымы жазылган открытка", "Ысымды жазып, бир басуу менен жөнөтүңүз"),
        ("settings", "Күн сайын жаңы тушкагаз", "Автоалмаштыруу жана майрам эскертүүлөрү"),
        ("feed_light", "Жарык жана караңгы тема", "Кыргызча, орусча жана англисче"),
    ],
    "en": [
        ("feed", "Kyrgyzstan on your phone", "New wallpapers and cards every day"),
        ("viewer", "High-quality wallpapers", "Home screen, lock screen or both"),
        ("cards", "Greeting cards for WhatsApp", "For Eid, Nooruz, Fridays and birthdays"),
        ("editor", "Cards with a name", "Add a name and send with one tap"),
        ("settings", "A new wallpaper every day", "Auto-change and holiday reminders"),
        ("feed_light", "Light and dark theme", "In Kyrgyz, Russian and English"),
    ],
}


def font(name, size, variation=None):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    if variation:
        f.set_variation_by_name(variation)
    return f


def background(size):
    """Тёмно-красный градиент с мягким свечением сверху"""
    w, h = size
    top, bottom = (110, 26, 32), (18, 10, 12)
    bg = Image.new("RGB", size)
    draw = ImageDraw.Draw(bg)
    for y in range(h):
        t = y / (h - 1)
        draw.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    glow = Image.new("L", size, 0)
    ImageDraw.Draw(glow).ellipse((w * 0.1, -h * 0.25, w * 0.9, h * 0.35), fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    bg = Image.composite(Image.new("RGB", size, (229, 72, 77)), bg, glow.point(lambda a: a // 2))
    return bg


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return mask


def phone(screen, width):
    """Экран в простой рамке телефона с тенью"""
    scale = width / screen.width
    screen = screen.resize((width, int(screen.height * scale)), Image.LANCZOS)
    bezel = 16
    outer = (screen.width + bezel * 2, screen.height + bezel * 2)
    frame = Image.new("RGBA", outer, (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, outer[0] - 1, outer[1] - 1), 66, fill=(12, 12, 14, 255),
                                            outline=(70, 70, 76, 255), width=3)
    frame.paste(screen, (bezel, bezel), rounded_mask(screen.size, 52))
    return frame


def wrap(draw, text, f, max_width):
    words, lines, line = text.split(), [], ""
    for word in words:
        test = (line + " " + word).strip()
        if draw.textlength(test, font=f) <= max_width or not line:
            line = test
        else:
            lines.append(line)
            line = word
    return lines + [line]


def compose(screen, title, subtitle):
    canvas = background((W, H)).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    title_font = font("Montserrat[wght].ttf", 72, b"ExtraBold")
    while max(draw.textlength(l, font=title_font) for l in wrap(draw, title, title_font, 960)) > 960 \
            or len(wrap(draw, title, title_font, 960)) > 2:
        title_font = font("Montserrat[wght].ttf", title_font.size - 4, b"ExtraBold")
    sub_font = font("Montserrat[wght].ttf", 38, b"Medium")
    title_lines = wrap(draw, title, title_font, 960)
    sub_lines = wrap(draw, subtitle, sub_font, 960)

    y = 90 if len(title_lines) == 1 else 60
    for line in title_lines:
        draw.text((W / 2, y), line, font=title_font, fill="white", anchor="ma")
        y += int(title_font.size * 1.15)
    # Тот же разделитель, что и на открытках
    y += 14
    draw.line([(W / 2 - 110, y), (W / 2 - 18, y)], fill=GOLD, width=3)
    draw.line([(W / 2 + 18, y), (W / 2 + 110, y)], fill=GOLD, width=3)
    draw.polygon([(W / 2, y - 9), (W / 2 + 9, y), (W / 2, y + 9), (W / 2 - 9, y)], fill=GOLD)
    y += 30
    for line in sub_lines:
        draw.text((W / 2, y), line, font=sub_font, fill=(255, 255, 255, 215), anchor="ma")
        y += int(sub_font.size * 1.3)

    device = phone(screen, 640)
    top = max(y + 40, 420)
    left = (W - device.width) // 2
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (left + 10, top + 30, left + device.width + 10, top + device.height + 30), 66, fill=(0, 0, 0, 170))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(28)))
    canvas.alpha_composite(device, (left, top))
    return canvas.convert("RGB")


def main():
    shots, lang = sys.argv[1], sys.argv[2]
    out = os.path.join(HERE, "screenshots", lang)
    os.makedirs(out, exist_ok=True)
    for i, (name, title, subtitle) in enumerate(CAPTIONS[lang], 1):
        screen = Image.open(os.path.join(shots, name + ".png")).convert("RGB")
        compose(screen, title, subtitle).save(os.path.join(out, "%02d.png" % i), optimize=True)
        print("готово", lang, "%02d" % i, title)


if __name__ == "__main__":
    main()
