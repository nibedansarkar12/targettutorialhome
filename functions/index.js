const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

exports.verifyStudent = onCall(
  {
    region: "us-central1",
    enforceAppCheck: false
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "প্রথমে Login করো।");
    }

    const phone = String(request.data?.phone || "").trim();
    const dob = String(request.data?.dob || "").trim();
    const uid = request.auth.uid;
    const email = request.auth.token.email || "";

    if (!/^[0-9]{10,15}$/.test(phone)) {
      throw new HttpsError("invalid-argument", "সঠিক ফোন নম্বর দাও।");
    }

    if (!/^\d{4}-\d{2}-\d{2}$/.test(dob)) {
      throw new HttpsError("invalid-argument", "জন্মতারিখ YYYY-MM-DD ফরম্যাটে দাও।");
    }

    if (!email) {
      throw new HttpsError("failed-precondition", "Login Account-এ Email পাওয়া যায়নি।");
    }

    const db = getFirestore();

    const matches = await db.collection("students")
      .where("phone", "==", phone)
      .where("dob", "==", dob)
      .limit(2)
      .get();

    if (matches.empty) {
      throw new HttpsError("not-found", "ফোন নম্বর অথবা জন্মতারিখ মিলছে না।");
    }

    if (matches.size > 1) {
      throw new HttpsError("failed-precondition", "একাধিক Student Record পাওয়া গেছে। Admin-এর সঙ্গে যোগাযোগ করো।");
    }

    const studentDoc = matches.docs[0];
    const student = studentDoc.data() || {};

    if (student.uid && student.uid !== uid) {
      throw new HttpsError("already-exists", "এই Student Record অন্য Account-এর সঙ্গে যুক্ত আছে।");
    }

    await studentDoc.ref.update({
      email: email,
      uid: uid,
      updatedAt: FieldValue.serverTimestamp()
    });

    return {
      success: true,
      studentId: studentDoc.id,
      name: String(student.name || "Student")
    };
  }
);
