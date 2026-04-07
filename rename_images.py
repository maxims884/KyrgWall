import os

# 🔧 Настройки
FOLDER_PATH = r"C:\Users\user\Desktop\wallpapers_hd\images\big"  # путь к папке с файлами
NEW_NAME = "Nature-00"  # новое базовое имя для файлов
START_NUMBER = 1     # с какого номера начинать
EXTENSIONS = (".jpg", ".jpeg", ".png")  # какие файлы переименовывать

# Получаем список файлов
files = [f for f in os.listdir(FOLDER_PATH) if f.lower().endswith(EXTENSIONS)]
files.sort()  # сортировка по имени (опционально)

# Переименование
number = START_NUMBER
for filename in files:
    old_path = os.path.join(FOLDER_PATH, filename)
    ext = os.path.splitext(filename)[1]  # сохраняем оригинальное расширение
    new_filename = f"{NEW_NAME}{number}{ext}"
    new_path = os.path.join(FOLDER_PATH, new_filename)

    try:
        os.rename(old_path, new_path)
        print(f"✅ {filename} → {new_filename}")
        number += 1
    except Exception as e:
        print(f"❌ Ошибка при переименовании {filename}: {e}")

print("\n🎉 Переименование завершено!")
