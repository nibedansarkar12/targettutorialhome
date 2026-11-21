
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const {
  getFirestore,
  FieldValue
} = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();

// Normalize Indian phone numbers
function normalizePhone(value) {
  let digits = String(value ?? "").replace(/\D/g, "");

  // Remove India country code
  if (digits.length === 12 && digits.startsWith("91")) {
    digits = digits.slice(2);
  }

  // Remove leading zero
  if (digits.length === 11 && digits.startsWith("0")) {
    digits = digits.slice(1);
  }

  return digits;
}

// Exam Web Login Token
exports.createExamWebToken = onCall(
  {
    region: "us-central1",
    enforceAppCheck: false
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "পরীক্ষা দিতে আগে অ্যাপে Login করো।"
      );
    }

    const uid = request.auth.uid;
    const token = await getAuth().createCustomToken(uid);

    return { token };
  }
);

// Student Verification
exports.verifyStudent = onCall(
  {
    region: "us-central1",
    enforceAppCheck: false
  },
  async (request) => {

    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "প্রথমে Login করো।"
      );
    }

    const uid = request.auth.uid;
    const email = request.auth.token.email || "";

    const phone = normalizePhone(request.data?.phone);
    const dob = String(request.data?.dob || "").trim();

    if (!/^\d{10}$/.test(phone)) {
      throw new HttpsError(
        "invalid-argument",
        "সঠিক ১০ সংখ্যার ফোন নম্বর দাও।"
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
        "Login Account-এর Email পাওয়া যায়নি।"
      );
    }

    // First search by exact DOB.
    // Then compare normalized phone numbers.
    const dobRecords = await db
      .collection("students")
      .where("dob", "==", dob)
      .get();

    const matches = dobRecords.docs.filter((doc) => {
      const student = doc.data() || {};
      return normalizePhone(student.phone) === phone;
    });

    if (matches.length === 0) {
      throw new HttpsError(
        "not-found",
        "ফোন নম্বর অথবা জন্মতারিখ মিলছে না।"
      );
    }

    if (matches.length > 1) {
      throw new HttpsError(
        "failed-precondition",
        "একাধিক Student Record পাওয়া গেছে। Admin-এর সঙ্গে যোগাযোগ করো।"
      );
    }

    const studentRef = matches[0].ref;
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

      if (
        normalizePhone(student.phone) !== phone ||
        String(student.dob || "").trim() !== dob
      ) {
        throw new HttpsError(
          "not-found",
          "Student Record পুনরায় যাচাই ব্যর্থ হয়েছে।"
        );
      }

      // Prevent another account from claiming this student
      if (student.uid && student.uid !== uid) {
        throw new HttpsError(
          "already-exists",
          "এই Student Record অন্য Account-এর সঙ্গে যুক্ত আছে।"
        );
      }

      // Prevent one account from claiming multiple students
      if (
        accountSnapshot.exists &&
        accountSnapshot.data().studentId !== studentId
      ) {
        throw new HttpsError(
          "already-exists",
          "এই Account ইতিমধ্যে অন্য Student-এর সঙ্গে যুক্ত আছে।"
        );
      }

      transaction.update(studentRef, {
        uid: uid,
        email: email,
        phoneNormalized: phone,
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
