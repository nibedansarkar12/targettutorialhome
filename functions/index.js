
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const {
  getFirestore,
  FieldValue
} = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();

exports.verifyStudent = onCall(
  {
    region: "us-central1",
    enforceAppCheck: false
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "প্রথমে Google Login করো।"
      );
    }

    const uid = request.auth.uid;
    const email = request.auth.token.email || "";

    const phone = String(request.data?.phone || "")
      .replace(/\D/g, "")
      .trim();

    const dob = String(request.data?.dob || "").trim();

    if (!/^[0-9]{10,15}$/.test(phone)) {
      throw new HttpsError(
        "invalid-argument",
        "সঠিক ফোন নম্বর দাও।"
      );
    }

    if (!/^\d{4}-\d{2}-\d{2}$/.test(dob)) {
      throw new HttpsError(
        "invalid-argument",
        "জন্মতারিখ YYYY-MM-DD ফরম্যাটে দাও।"
      );
    }

    if (!email) {
      throw new HttpsError(
        "failed-precondition",
        "Google Account-এর Email পাওয়া যায়নি।"
      );
    }

    const matches = await db
      .collection("students")
      .where("phone", "==", phone)
      .where("dob", "==", dob)
      .limit(2)
      .get();

    if (matches.empty) {
      throw new HttpsError(
        "not-found",
        "ফোন নম্বর অথবা জন্মতারিখ মিলছে না।"
      );
    }

    if (matches.size > 1) {
      throw new HttpsError(
        "failed-precondition",
        "একাধিক Student Record পাওয়া গেছে। Admin-এর সঙ্গে যোগাযোগ করো।"
      );
    }

    const studentRef = matches.docs[0].ref;
    const studentId = studentRef.id;

    // One Google Account = One Student
    const accountRef = db
      .collection("studentAccountLinks")
      .doc(uid);

    const result = await db.runTransaction(async (transaction) => {
      const studentSnapshot = await transaction.get(studentRef);
      const accountSnapshot = await transaction.get(accountRef);

      if (!studentSnapshot.exists) {
        throw new HttpsError(
          "not-found",
          "Student Record পাওয়া যায়নি।"
        );
      }

      const student = studentSnapshot.data() || {};

      // A Student cannot be claimed by another account.
      if (student.uid && student.uid !== uid) {
        throw new HttpsError(
          "already-exists",
          "এই Student Record অন্য Google Account-এর সঙ্গে যুক্ত আছে।"
        );
      }

      // One account cannot claim another student.
      if (
        accountSnapshot.exists &&
        accountSnapshot.data().studentId !== studentId
      ) {
        throw new HttpsError(
          "already-exists",
          "এই Google Account ইতিমধ্যে অন্য Student-এর সঙ্গে যুক্ত আছে।"
        );
      }

      transaction.update(studentRef, {
        uid: uid,
        email: email,
        verifiedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      });

      if (!accountSnapshot.exists) {
        transaction.set(accountRef, {
          studentId: studentId,
          createdAt: FieldValue.serverTimestamp()
        });
      }

      return {
        success: true,
        studentId: studentId,
        name: String(student.name || "Student")
      };
    });

    return result;
  }
);
