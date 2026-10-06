"""Бот контента для "Кыргызстан обои".

Каждый день добавляет 1 новые обои и 1–2 открытки: загружает файлы в Firebase Storage
и создаёт документы в Firestore, которые сразу видны в приложении.

    python bot.py daily                  # обычный ежедневный запуск
    python bot.py seed-cards --count 30  # первичное заполнение открыток
    python bot.py wallpapers --count 5   # добавить несколько обоев сразу

С --dry-run ничего не загружается: картинки сохраняются в папку out/, чтобы посмотреть результат.
"""
import argparse
import datetime
import io
import json
import os
import random
import sys
import time
import urllib.parse
import uuid

from PIL import Image

import cards
import sources
import texts

BRAND = {"ky": "Кыргызстан тушкагаздары", "ru": "Кыргызстан обои"}
WALLPAPER_TYPES = ["nature", "animals", "arch", "relig", "stars"]
OUT = os.path.join(os.path.dirname(__file__), "out")
MAX_PIXELS = 4_000_000  # ~1600x2500: хватает для экрана телефона, файл ~500 КБ


class Store:
    """Куда складываем результат: Firebase или локальная папка при --dry-run"""

    def __init__(self, dry_run):
        self.dry_run = dry_run
        if dry_run:
            os.makedirs(OUT, exist_ok=True)
            self.state_path = os.path.join(OUT, "state.json")
            self.used = set(json.load(open(self.state_path))) if os.path.exists(self.state_path) else set()
            self.last_daily = None
            return
        import firebase_admin
        from firebase_admin import credentials, firestore, storage
        raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT")
        cred = credentials.Certificate(json.loads(raw)) if raw else credentials.ApplicationDefault()
        self.bucket_name = os.environ.get("FIREBASE_BUCKET", "hd-wallpaper-b78a8.appspot.com")
        firebase_admin.initialize_app(cred, {"storageBucket": self.bucket_name})
        self.firestore = firestore
        self.db = firestore.client()
        self.bucket = storage.bucket()
        # Какие источники уже использованы, чтобы не загружать одно и то же дважды
        self.state = self.db.collection("bot").document("state")
        snapshot = self.state.get()
        data = snapshot.to_dict() if snapshot.exists else {}
        self.used = set(data.get("used", []))
        # Дата последнего ежедневного запуска по расписанию
        self.last_daily = data.get("lastDaily")

    def mark_daily(self, day):
        if not self.dry_run:
            self.state.set({"lastDaily": day.isoformat()}, merge=True)

    def _upload(self, path, data):
        blob = self.bucket.blob(path)
        blob.cache_control = "public, max-age=31536000"
        # Токен нужен, чтобы ссылка открывалась так же, как у загруженных вручную картинок
        blob.metadata = {"firebaseStorageDownloadTokens": str(uuid.uuid4())}
        blob.upload_from_string(data, content_type="image/jpeg")
        return "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media" % (
            self.bucket_name, urllib.parse.quote(path, safe=""))

    def add(self, collection, name, image, small, fields, source_id):
        if self.dry_run:
            image_path = os.path.join(OUT, "%s_%s.jpg" % (collection, name))
            open(image_path, "wb").write(image)
            json.dump(fields, open(image_path + ".json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
            self.used.add(source_id)
            json.dump(sorted(self.used), open(self.state_path, "w"))
            print("  сохранено", image_path)
            return
        doc = dict(fields)
        doc["url"] = self._upload("%s/%s.jpg" % (collection, name), image)
        doc["urlSmall"] = self._upload("%s/%ss.jpg" % (collection, name), small)
        doc["type"] = collection
        doc["createdAt"] = self.firestore.SERVER_TIMESTAMP
        self.db.collection(collection).document(name).set(doc)
        self.state.set({"used": self.firestore.ArrayUnion([source_id])}, merge=True)
        self.used.add(source_id)
        print("  загружено", collection, name)


def file_name(kind, item_id):
    """Имя файла и документа.

    Приложение сортирует категории по ссылке на картинку, а старые файлы называются
    "Nature-001.jpg". Имя "0auto-<обратное время>" идёт в сортировке раньше любых букв,
    и чем новее картинка, тем меньше число — так новые оказываются сверху даже
    у тех, кто не обновил приложение.
    """
    reverse_time = 9_999_999_999 - int(time.time())
    return "0auto-%010d-%s-%s" % (reverse_time, kind, item_id)


def jpeg(image, quality):
    out = io.BytesIO()
    image.save(out, "JPEG", quality=quality, optimize=True, progressive=True)
    return out.getvalue()


def prepare(image):
    """Основная картинка до ~5.7 Мп и крошечная миниатюра-заглушка, как у остальных"""
    image = image.convert("RGB")
    if image.width * image.height > MAX_PIXELS:
        scale = (MAX_PIXELS / (image.width * image.height)) ** 0.5
        image = image.resize((int(image.width * scale), int(image.height * scale)), Image.LANCZOS)
    small = image.copy()
    small.thumbnail((48, 48))
    return jpeg(image, 80), jpeg(small, 30)


def add_wallpaper(store, today):
    candidates = sources.commons_candidates(store.used, limit=10)
    if not candidates:
        candidates = [dict(c, type="nature") for c in sources.openverse_candidates("kyrgyzstan", store.used)]
    for item in candidates:
        try:
            image = Image.open(io.BytesIO(sources.download(item["download"])))
        except Exception as e:
            print("  не скачалось", item["id"], e)
            continue
        main, small = prepare(image)
        name = file_name("wall", item["id"])
        fields = {k: item[k] for k in ("tags", "author", "license", "sourceUrl")}
        print("обои:", item["type"], item["license"], item["author"][:40])
        store.add(item["type"], name, main, small, fields, item["id"])
        return True
    print("обои: подходящих картинок не нашлось")
    return False


used_variants = {}


def add_card(store, occasion, today, index):
    rnd = random.Random("%s-%s-%d" % (today.isoformat(), occasion, index))
    data = texts.OCCASIONS[occasion]
    lang = "ky" if rnd.random() < 0.6 else "ru"
    # Варианты текста берём по очереди, чтобы в одной партии не было одинаковых открыток подряд
    key = (occasion, lang)
    used_variants[key] = used_variants.get(key, rnd.randrange(len(data[lang]))) + 1
    title, subtitle = data[lang][used_variants[key] % len(data[lang])]
    queries = list(data["backgrounds"])
    rnd.shuffle(queries)
    for query in queries:
        # Сначала вертикальные фото, если таких нет — любые (открытка обрежет по центру)
        candidates = sources.openverse_candidates(query, store.used, portrait=True) or             sources.openverse_candidates(query, store.used)
        for item in candidates:
            try:
                background = sources.download(item["download"])
                if not cards.is_good_background(background):
                    print("  фон слишком тёмный или однотонный", item["id"])
                    continue
                card = cards.render(background, title, subtitle, BRAND[lang], seed=item["id"])
            except Exception as e:
                print("  фон не подошёл", item["id"], e)
                continue
            main, small = prepare(card)
            name = file_name(occasion, index)
            time.sleep(1)  # чтобы у открыток одного запуска были разные имена
            tags = [occasion, title.strip("!"), "открытка", "ачык кат", "card"]
            fields = {"tags": tags, "occasion": occasion, "lang": lang,
                      "backgroundSource": item["sourceUrl"], "license": item["license"]}
            print("открытка:", occasion, lang, title)
            store.add("cards", name, main, small, fields, item["id"])
            return True
        time.sleep(1)
    print("открытка: не нашлось фона для", occasion)
    return False



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["daily", "daily-cards", "seed-cards", "wallpapers"])
    parser.add_argument("--count", type=int, default=30)
    parser.add_argument("--dry-run", action="store_true")
    # Запуск по расписанию: не больше одного раза в день. GitHub может задержать или пропустить
    # запуск, поэтому расписаний два, и второй выходит сразу, если первый уже отработал
    parser.add_argument("--scheduled", action="store_true")
    args = parser.parse_args()

    store = Store(args.dry_run)
    # Дата по Бишкеку (UTC+6): серверы GitHub живут по UTC
    today = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=6)).date()

    if args.scheduled and store.last_daily == today.isoformat():
        print("сегодня (%s) бот уже отработал, пропускаю" % today)
        return 0

    if args.command in ("daily", "daily-cards"):
        # daily-cards — без обоев: пока у пользователей старая версия приложения,
        # где нет подписи автора, обязательной для фото с Wikimedia Commons
        if args.command == "daily":
            add_wallpaper(store, today)
        for i, occasion in enumerate(texts.occasions_for(today)):
            add_card(store, occasion, today, i)
        # Отмечаем день только после успешной загрузки: если запуск упал, второй попробует снова
        if args.scheduled:
            store.mark_daily(today)
    elif args.command == "wallpapers":
        for i in range(args.count):
            add_wallpaper(store, today)
            time.sleep(1)
    elif args.command == "seed-cards":
        # Для первичного наполнения: по одной на каждый праздник, остальное — повседневные поводы
        everyday = ["morning", "evening", "friday", "birthday"]
        plan = (list(texts.HOLIDAYS) + everyday * args.count)[:args.count]
        for i, occasion in enumerate(plan):
            add_card(store, occasion, today, 100 + i)
    return 0


if __name__ == "__main__":
    sys.exit(main())
