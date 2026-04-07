import os
from PIL import Image
from io import BytesIO

SOURCE_DIR = r"C:\Users\user\Desktop\wallpapers_hd\images\big"
OUTPUT_DIR = os.path.join(SOURCE_DIR, "small")
os.makedirs(OUTPUT_DIR, exist_ok=True)

def compress_to_target_size(img_path, target_kb=50):
    """Сжимает изображение примерно до target_kb КБ с дебагом."""
    print(f"\n🔹 Обработка файла: {img_path}")
    
    with Image.open(img_path) as img:
        img = img.convert("RGB")  # убрать альфу
        quality = 35
        step = 1
        buffer = BytesIO()

        while quality > 1:
            buffer.seek(0)
            buffer.truncate()
            img.save(buffer, format="JPEG", optimize=True, quality=quality)
            size_kb = len(buffer.getvalue()) / 1024
            print(f"   Пробуем quality={quality} → размер={size_kb:.2f} KB")
            if size_kb <= target_kb:
                print(f"   ✅ Достигнута цель: {size_kb:.2f} KB")
                return buffer.getvalue()
            quality -= step

        print(f"   ⚠️ Минимальный quality достигнут, размер={size_kb:.2f} KB")
        return buffer.getvalue()  # если не удалось достичь цели

for filename in os.listdir(SOURCE_DIR):
    if filename.lower().endswith((".jpg", ".jpeg", ".png")):
        src_path = os.path.join(SOURCE_DIR, filename)
        name, ext = os.path.splitext(filename)
        output_name = f"{name}s.jpg"
        output_path = os.path.join(OUTPUT_DIR, output_name)

        print(f"\n➡️ Исходный файл: {src_path}")
        print(f"   Будет сохранён как: {output_path}")

        try:
            compressed_data = compress_to_target_size(src_path, target_kb=1)
            with open(output_path, "wb") as f:
                f.write(compressed_data)
            size_kb = len(compressed_data) / 1024
            print(f"✅ Сохранено: {output_name}, размер={size_kb:.2f} KB")
        except Exception as e:
            print(f"❌ Ошибка с {filename}: {e}")

print("\n🎉 Готово! Все сжатые изображения сохранены в папке 'small'.")
