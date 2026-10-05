"""Где брать картинки законно.

Wikimedia Commons — фото Кыргызстана под свободными лицензиями. Для CC BY / CC BY-SA
обязательно указывать автора и лицензию: приложение показывает их под картинкой.
Openverse — только CC0 и public domain: никаких условий, поэтому из них делаем фоны открыток.
"""
import html
import random
import re

import requests

USER_AGENT = "KyrgWallContentBot/1.0 (https://github.com/maxims884/KyrgWall)"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
OPENVERSE_API = "https://api.openverse.org/v1/images/"

session = requests.Session()
session.headers["User-Agent"] = USER_AGENT

# Порядок важен: сначала отобранные сообществом качественные фото, потом все пейзажи
COMMONS_QUERIES = [
    'deepcat:"Quality_images_of_Kyrgyzstan" filew:>1600',
    'deepcat:"Landscapes_of_Kyrgyzstan" filew:>1800',
    'deepcat:"Lakes_of_Kyrgyzstan" filew:>1800',
    'deepcat:"Buildings_in_Bishkek" filew:>1800',
]

# Фото с узнаваемыми людьми в коммерческом приложении без их согласия не берём
PEOPLE_WORDS = ("people", "portrait", "women", "men ", "men of", "children", "girls", "boys",
                "persons", "faces", "selfie", "musicians", "dancers", "wrestlers", "athletes")

# Служебные категории Commons — для поиска бесполезны
SERVICE_CATEGORIES = (r"^(Images |Self-published|Uploaded|Files |Media |CC-|Taken with|Photographs by|"
                      r"Pages |Quality images|Featured|Valued|Wiki Loves|Supported by|Created with|"
                      r"License|GFDL|Photos by|Panoramics|Pictures by|Scans)")

TYPE_WORDS = [
    ("relig", ("mosque", "minaret", "church", "mausoleum", "religio", "madrasa", "cathedral")),
    ("animals", ("horse", "animal", "fauna", "bird", "eagle", "sheep", "yak", "dog", "cattle", "camel", "goat")),
    ("arch", ("building", "architecture", "street", "bishkek", "monument", "square", "house", "city",
              "town", "bridge", "palace", "museum", "statue")),
]

# Перевод частых тегов, чтобы поиск в приложении находил их по-русски и по-кыргызски
TAG_TRANSLATIONS = {
    "lake": ["озеро", "көл"], "mountain": ["горы", "тоо"], "river": ["река", "дарыя"],
    "issyk-kul": ["Иссык-Куль", "Ысык-Көл"], "bishkek": ["Бишкек"], "osh": ["Ош"],
    "yurt": ["юрта", "боз үй"], "horse": ["лошадь", "ат"], "winter": ["зима", "кыш"],
    "snow": ["снег", "кар"], "sunset": ["закат"], "forest": ["лес", "токой"],
    "canyon": ["каньон"], "waterfall": ["водопад", "шаркыратма"], "song-kul": ["Сон-Куль", "Соң-Көл"],
    "ala-archa": ["Ала-Арча"], "glacier": ["ледник", "мөңгү"], "valley": ["долина", "өрөөн"],
    "mosque": ["мечеть", "мечит"], "monument": ["памятник", "эстелик"],
}


def _clean(text):
    return html.unescape(re.sub(r"<[^>]+>", "", text or "")).strip()


def _type_and_tags(categories, title):
    text = " ".join(categories + [title]).lower()
    picture_type = "nature"
    for candidate, words in TYPE_WORDS:
        if any(w in text for w in words):
            picture_type = candidate
            break
    tags = []
    for key, translations in TAG_TRANSLATIONS.items():
        if key in text:
            tags += [key.capitalize()] + translations
    for c in categories[:10]:
        if re.match(SERVICE_CATEGORIES, c):
            continue
        short = re.sub(r"\s+(of|in)\s+Kyrgyzstan.*$", "", c).strip()
        if 2 < len(short) < 30:
            tags.append(short)
    return picture_type, list(dict.fromkeys(tags))[:15]


def commons_candidates(used, limit=20):
    """Свободные по лицензии и ещё не использованные фото с Commons"""
    found = []
    for query in COMMONS_QUERIES:
        offset = 0
        while len(found) < limit and offset < 2000:
            r = session.get(COMMONS_API, params={
                "action": "query", "format": "json", "generator": "search", "gsrsearch": query,
                "gsrnamespace": 6, "gsrlimit": 50, "gsroffset": offset,
                "prop": "imageinfo", "iiprop": "url|size|extmetadata|mime",
                "iiurlwidth": 4000, "iiurlheight": 2400,
            }, timeout=60)
            r.raise_for_status()
            data = r.json()
            pages = data.get("query", {}).get("pages", {})
            if not pages:
                break
            for page in sorted(pages.values(), key=lambda p: p.get("index", 0)):
                item = _commons_item(page)
                if item and item["id"] not in used:
                    found.append(item)
            if "continue" not in data:
                break
            offset = data["continue"]["gsroffset"]
        if len(found) >= limit:
            break
    return found


def _commons_item(page):
    info = (page.get("imageinfo") or [{}])[0]
    meta = info.get("extmetadata", {})
    value = lambda key: meta.get(key, {}).get("value", "")
    if info.get("mime") not in ("image/jpeg", "image/png"):
        return None
    if min(info.get("width", 0), info.get("height", 0)) < 1080:
        return None
    if "personality" in value("Restrictions").lower():
        return None
    license_name = _clean(value("LicenseShortName"))
    if not re.match(r"^(CC0|Public domain|PD|CC BY(-SA)? [\d.]+)", license_name, re.I):
        return None
    categories = [c.strip() for c in value("Categories").split("|") if c.strip()]
    if any(w in c.lower() for c in categories for w in PEOPLE_WORDS):
        return None
    title = page["title"].replace("File:", "")
    picture_type, tags = _type_and_tags(categories, title)
    return {
        "id": "commons-%s" % page["pageid"],
        "download": info.get("thumburl") or info["url"],
        "type": picture_type,
        "tags": tags,
        "author": _clean(value("Artist"))[:120] or "Wikimedia Commons",
        "license": license_name,
        "sourceUrl": info.get("descriptionurl", ""),
    }


def openverse_candidates(query, used, portrait=None, limit=20):
    """Фото без каких-либо условий использования (CC0 и public domain)"""
    # Только фотографии с фотостоков: в остальных источниках много музейных предметов
    params = {"q": query, "license": "cc0,pdm", "size": "large", "page_size": 20,
              "category": "photograph", "source": "stocksnap,rawpixel,wordpress",
              "page": random.randint(1, 3), "mature": "false"}
    if portrait is not None:
        params["aspect_ratio"] = "tall" if portrait else "wide"
    r = session.get(OPENVERSE_API, params=params, timeout=60)
    if r.status_code != 200 and params["page"] > 1:
        params["page"] = 1
        r = session.get(OPENVERSE_API, params=params, timeout=60)
    r.raise_for_status()
    items = []
    for x in r.json().get("results", []):
        item_id = "openverse-" + x["id"]
        if item_id in used or not x.get("url"):
            continue
        if min(x.get("width") or 0, x.get("height") or 0) < 1080:
            continue
        tags = [t["name"] for t in (x.get("tags") or []) if t.get("name")]
        text = " ".join(tags + [x.get("title") or ""]).lower()
        # Поиск Openverse широкий: берём фото, только если запрос есть в его описании
        if not any(re.search(r"\b%s" % re.escape(w), text) for w in query.lower().split()):
            continue
        if re.search(r"\b(people|person|persons|woman|women|man|men|girl|girls|boy|boys|child|children|"
                     r"portrait|face|selfie|bride|groom|couple|family|model|hands?)\b", text):
            continue
        # На Rawpixel много старинных гравюр и рисунков — для открыток нужны современные фото
        if re.search(r"\b(illustration|drawing|engraving|vintage|antique|painting|sketch|print|poster|"
                     r"lithograph|etching|museum|manuscript|plate|watercolor|art)\b", text):
            continue
        items.append({
            "id": item_id,
            "download": x["url"],
            "tags": tags[:10],
            "author": _clean(x.get("creator"))[:120],
            "license": "CC0" if x.get("license") == "cc0" else "Public domain",
            "sourceUrl": x.get("foreign_landing_url") or "",
        })
        if len(items) >= limit:
            break
    return items


def download(url):
    r = session.get(url, timeout=120)
    r.raise_for_status()
    return r.content
