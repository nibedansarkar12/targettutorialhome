
const { onCall, HttpsError } = require("firebase-functions/v2/https");

const { initializeApp } = require("firebase-admin/app");

const { getAuth } = require("firebase-admin/auth");

const {
  getFirestore,
  FieldValue
} = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();


// ======================================
// CREATE EXAM WEB TOKEN
// ======================================

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

    try {
      const token = await getAuth().createCustomToken(uid);

      return {
        success: true,
        token: token
      };

    } catch (error) {
      console.error("Exam token error:", error);

      throw new HttpsError(
        "internal",
        "পরীক্ষার Login তৈরি করা যায়নি।"
      );
    }
  }
);


// ======================================
// VERIFY STUDENT
// ======================================

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


    // Validate phone number

    if (!/^[0-9]{10,15}$/.test(phone)) {
      throw new HttpsError(
        "invalid-argument",
        "সঠিক ফোন নম্বর দাও।"
      );
    }


    // Validate date of birth

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


    // Find student

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


    const result = await db.runTransaction(
      async (transaction) => {

        const studentSnapshot =
          await transaction.get(studentRef);

        const accountSnapshot =
          await transaction.get(accountRef);


        if (!studentSnapshot.exists) {
          throw new HttpsError(
            "not-found",
            "Student Record পাওয়া যায়নি।"
          );
        }


        const student = studentSnapshot.data() || {};


        // Prevent another account from claiming this student

        if (student.uid && student.uid !== uid) {
          throw new HttpsError(
            "already-exists",
            "এই Student Record অন্য Google Account-এর সঙ্গে যুক্ত আছে।"
          );
        }


        // Prevent one account from claiming another student

        if (
          accountSnapshot.exists &&
          accountSnapshot.data().studentId !== studentId
        ) {
          throw new HttpsError(
            "already-exists",
            "এই Google Account ইতিমধ্যে অন্য Student-এর সঙ্গে যুক্ত আছে।"
          );
        }


        // Update student information

        transaction.update(studentRef, {
          uid: uid,
          email: email,
          verifiedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp()
        });


        // Save account link

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

      }
    );


    return result;

  }
);
