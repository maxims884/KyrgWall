const {onRequest} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

exports.syncNatureToday = onRequest(async (req, res) => {
  try {
    const bucket = admin.storage().bucket();
    const db = admin.firestore();

    const prefix = "nature/";
    const [files] = await bucket.getFiles({prefix});

    const today = new Date();
    const startOfDay = new Date(
        today.getFullYear(),
        today.getMonth(),
        today.getDate(),
    ).getTime();

    const endOfDay = startOfDay + 24 * 60 * 60 * 1000;

    const fileMap = new Map();
    for (const file of files) {
      fileMap.set(file.name, file);
    }

    const batch = db.batch();

    for (const file of files) {
      const name = file.name;

      // пропускаем миниатюры
      if (name.endsWith("s.jpg") || name.endsWith("s.png")) continue;

      const metadata = file.metadata;
      if (!metadata || !metadata.updated) continue;

      const updatedTime = new Date(metadata.updated).getTime();

      // фильтр: только сегодня
      if (updatedTime < startOfDay || updatedTime > endOfDay) continue;

      // имя миниатюры
      const extIndex = name.lastIndexOf(".");
      const base = name.substring(0, extIndex);
      const ext = name.substring(extIndex);
      const smallName = `${base}s${ext}`;

      const smallFile = fileMap.get(smallName);
      if (!smallFile) continue;

      // ID документа
      const docId = name.replace(prefix, "");
      const docRef = db.collection("nature").doc(docId);

      // проверка на дубли
      const doc = await docRef.get();
      if (doc.exists) continue;

      // ✅ формируем download URL (без signed URL)
      const url = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(name)}?alt=media`;

      const urlSmall = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(smallName)}?alt=media`;

      batch.set(docRef, {
        type: "nature",
        url: url,
        urlSmall: urlSmall,
      });
    }

    await batch.commit();

    res.status(200).send("Nature synced without duplicates");
  } catch (err) {
    console.error(err);
    res.status(500).send("Error");
  }
});
