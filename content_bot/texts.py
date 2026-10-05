"""Поздравления для открыток и даты праздников.

Кыргызские тексты стоит показать носителю языка и поправить прямо здесь.
"""
import datetime

# Поводы для открыток: заголовок и пожелание на кыргызском и русском,
# плюс запросы для поиска фона (фото CC0 / public domain на Openverse)
OCCASIONS = {
    "morning": {
        "ky": [("Кутмандуу таң!", "Күнүңүз ийгиликтүү өтсүн"), ("Кутман таң!", "Жакшы маанай каалайм"),
               ("Кайырлуу таң!", "Маанайыңыз көтөрүңкү болсун")],
        "ru": [("Доброе утро!", "Хорошего дня и отличного настроения"), ("С добрым утром!", "Пусть день будет удачным"),
               ("Доброго утра!", "Улыбок и тепла")],
        "backgrounds": ["sunrise", "morning flowers", "morning coffee", "sunrise mountains"],
    },
    "evening": {
        "ky": [("Бейпил түн!", "Тынч уктаңыз"), ("Кайырлуу кеч!", "Жакшы эс алыңыз"),
               ("Таттуу түш көрүңүз!", "Бейпил түн каалайм")],
        "ru": [("Спокойной ночи!", "Сладких снов"), ("Добрый вечер!", "Уютного вечера"),
               ("Доброй ночи!", "Пусть сны будут светлыми")],
        "backgrounds": ["night sky stars", "moon night", "starry night", "candle evening"],
    },
    "friday": {
        "ky": [("Жума маарек болсун!", "Дубаларыңыз кабыл болсун"), ("Жумаңыз мубарак!", "Үйүңүзгө береке, тынчтык")],
        "ru": [("Благословенной пятницы!", "Пусть молитвы будут приняты"), ("Джума мубарак!", "Мира и благополучия вашему дому")],
        "backgrounds": ["mosque", "mosque sunset", "mosque night", "crescent moon"],
    },
    "birthday": {
        "ky": [("Туулган күнүңүз менен!", "Бакыт, ден соолук, ийгилик каалайм"),
               ("Туулган күнүң менен!", "Тилегениң орундалсын"),
               ("Туулган күнүңүз кутман болсун!", "Узак өмүр, бакыт каалайм")],
        "ru": [("С днём рождения!", "Счастья, здоровья и удачи"), ("С днём рождения!", "Пусть сбываются мечты"),
               ("Поздравляю с днём рождения!", "Радости и тепла каждый день")],
        "backgrounds": ["birthday cake", "balloons", "flowers bouquet", "roses"],
    },
    "nooruz": {
        "ky": [("Нооруз майрамыңыз менен!", "Жаз келди — бакыт келди")],
        "ru": [("С праздником Нооруз!", "Пусть весна принесёт счастье и достаток")],
        "backgrounds": ["tulips", "spring flowers", "blossom"],
    },
    "orozo_ait": {
        "ky": [("Орозо айт майрамыңыз менен!", "Орозо-намазыңыз кабыл болсун")],
        "ru": [("С праздником Орозо айт!", "Пусть пост и молитвы будут приняты")],
        "backgrounds": ["mosque", "crescent moon", "ramadan"],
    },
    "kurman_ait": {
        "ky": [("Курман айт майрамыңыз менен!", "Курмандыгыңыз кабыл болсун")],
        "ru": [("С праздником Курман айт!", "Пусть жертва будет принята")],
        "backgrounds": ["mosque", "mosque sunset", "crescent moon"],
    },
    "new_year": {
        "ky": [("Жаңы жылыңыз менен!", "Жаңы бакыт, жаңы ийгилик")],
        "ru": [("С Новым годом!", "Пусть год будет счастливым")],
        "backgrounds": ["christmas lights", "snow winter", "fireworks"],
    },
    "mar8": {
        "ky": [("8-Март майрамыңыз менен!", "Сулуу, бактылуу болуңуз")],
        "ru": [("С 8 Марта!", "Весеннего настроения и улыбок")],
        "backgrounds": ["tulips", "flowers bouquet", "mimosa"],
    },
    "feb23": {
        "ky": [("23-февраль майрамыңыз менен!", "Күч-кубат, ден соолук")],
        "ru": [("С 23 февраля!", "Силы, здоровья и удачи")],
        "backgrounds": ["mountains", "snow mountains"],
    },
    "victory": {
        "ky": [("Жеңиш күнүңүз менен!", "Баатырлардын эрдиги унутулбайт")],
        "ru": [("С Днём Победы!", "Помним и гордимся")],
        "backgrounds": ["fireworks", "carnations", "red tulips"],
    },
    "independence": {
        "ky": [("Эгемендүүлүк күнүңүз менен!", "Кыргызстаным, гүлдөй бер!")],
        "ru": [("С Днём независимости!", "Процветания нашему Кыргызстану")],
        "backgrounds": ["kyrgyzstan", "mountains", "fireworks"],
    },
}

# Те же даты, что и в приложении (HolidaysKt). Айты — по объявлению муфтията, сверять каждый год
HOLIDAYS = {
    "new_year": ["01-01"],
    "feb23": ["02-23"],
    "mar8": ["03-08"],
    "nooruz": ["03-21"],
    "victory": ["05-09"],
    "independence": ["08-31"],
    "orozo_ait": ["2026-03-20", "2027-03-10", "2028-02-27", "2029-02-14", "2030-02-05"],
    "kurman_ait": ["2026-05-27", "2027-05-16", "2028-05-05", "2029-04-24", "2030-04-13"],
}

EVERYDAY = ["morning", "birthday", "evening", "morning", "birthday", "friday"]


def upcoming_holiday(today: datetime.date, days: int = 10):
    """Ближайший праздник в пределах days дней, иначе None"""
    for shift in range(days + 1):
        day = today + datetime.timedelta(days=shift)
        full, short = day.isoformat(), day.isoformat()[5:]
        for holiday, dates in HOLIDAYS.items():
            if full in dates or short in dates:
                return holiday
    return None


def occasions_for(today: datetime.date):
    """Какие открытки делать сегодня: 1 обычно, 2 перед праздником и в четверг (к пятнице)"""
    holiday = upcoming_holiday(today)
    everyday = EVERYDAY[today.toordinal() % len(EVERYDAY)]
    if holiday:
        return [holiday, everyday]
    if today.weekday() == 3:
        return ["friday", everyday if everyday != "friday" else "morning"]
    return [everyday]
