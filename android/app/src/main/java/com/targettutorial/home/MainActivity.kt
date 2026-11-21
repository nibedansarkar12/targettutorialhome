package com.targettutorial.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private val Navy = Color(0xFF0B1F3A)
private val Blue = Color(0xFF2563EB)
private val Bg = Color(0xFFF4F7FB)

class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var page by mutableStateOf("welcome")
    private var message by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var studentName by mutableStateOf("Student")
    private var studentId by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(11, 31, 58)
        window.navigationBarColor = android.graphics.Color.rgb(11, 31, 58)

        val savedId = getPreferences(MODE_PRIVATE)
            .getString("student_id", null)

        if (auth.currentUser != null) {
            if (savedId != null) {
                studentId = savedId
                loadStudent(savedId)
            } else {
                page = "verify"
            }
        }

        setContent {
            TargetTheme {
                AppScreen()
            }
        }
    }

    private fun loadStudent(id: String) {
        db.collection("students").document(id).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    studentName = snap.getString("name") ?: "Student"
                    studentId = id
                    page = "dashboard"
                } else {
                    page = "verify"
                }
            }
            .addOnFailureListener {
                message = it.message ?: "তথ্য পাওয়া যায়নি"
                page = "verify"
            }
    }

    private fun signup(
        name: String,
        phone: String,
        dob: String,
        email: String,
        password: String,
        education: String,
        status: String
    ) {
        if (name.isBlank() || phone.isBlank() ||
            dob.isBlank() || email.isBlank() ||
            password.length < 6
        ) {
            message = "সব তথ্য পূরণ করো। Password কমপক্ষে ৬ অক্ষরের হতে হবে।"
            return
        }

        busy = true
        message = ""

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->

                val user = result.user ?: run {
                    busy = false
                    message = "Account তৈরি হয়নি"
                    return@addOnSuccessListener
                }

                val uidCode = name.first().uppercase() +
                    (1000..9999).random().toString()

                val data = hashMapOf(
                    "uid" to uidCode,
                    "name" to name,
                    "whatsapp" to phone,
                    "email" to email,
                    "dob" to dob,
                    "education" to education,
                    "status" to status,
                    "subscribe" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                db.collection("users").add(data)
                    .addOnSuccessListener {
                        busy = false
                        message = "Signup সফল হয়েছে। এখন Phone ও DOB Verify করো।"
                        page = "verify"
                    }
                    .addOnFailureListener {
                        busy = false
                        message = "তথ্য সেভ হয়নি: ${it.message}"
                    }
            }
            .addOnFailureListener {
                busy = false
                message = it.message ?: "Signup ব্যর্থ হয়েছে"
            }
    }

    private fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            message = "Email ও Password লিখো"
            return
        }

        busy = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                busy = false
                message = ""
                page = "verify"
            }
            .addOnFailureListener {
                busy = false
                message = it.message ?: "Login ব্যর্থ হয়েছে"
            }
    }

    private fun googleLogin() {
        try {
            val options = GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(
                    getString(R.string.default_web_client_id)
                )
                .requestEmail()
                .build()

            val client = GoogleSignIn.getClient(this, options)
            startActivityForResult(client.signInIntent, 1001)

        } catch (e: Exception) {
            message = e.message ?: "Google Login শুরু হয়নি"
        }
    }

    @Deprecated("Google Sign In result")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(
                    com.google.android.gms.common.api.ApiException::class.java
                )

                val credential = GoogleAuthProvider.getCredential(
                    account.idToken, null
                )

                busy = true

                auth.signInWithCredential(credential)
                    .addOnSuccessListener { result ->

                        val user = result.user!!

                        db.collection("users")
                            .whereEqualTo("uid", user.uid)
                            .get()
                            .addOnSuccessListener { snap ->

                                if (snap.isEmpty) {
                                    db.collection("users").add(
                                        mapOf(
                                            "uid" to user.uid,
                                            "name" to user.displayName,
                                            "email" to user.email,
                                            "createdAt" to FieldValue.serverTimestamp(),
                                            "google" to true
                                        )
                                    )
                                }

                                busy = false
                                page = "verify"
                            }
                            .addOnFailureListener {
                                busy = false
                                message = it.message ?: "তথ্য সেভ হয়নি"
                            }
                    }
                    .addOnFailureListener {
                        busy = false
                        message = it.message ?: "Google Login ব্যর্থ"
                    }

            } catch (e: Exception) {
                busy = false
                message = e.message ?: "Google Login ব্যর্থ"
            }
        }
    }

    private fun verifyStudent(phone: String, dob: String) {
        if (phone.isBlank() || dob.isBlank()) {
            message = "Phone ও Date of Birth দাও"
            return
        }

        val user = auth.currentUser ?: run {
            message = "প্রথমে Login করো"
            return
        }

        busy = true

        db.collection("students")
            .whereEqualTo("phone", phone)
            .whereEqualTo("dob", dob)
            .get()
            .addOnSuccessListener { snap ->

                if (snap.isEmpty) {
                    busy = false
                    message = "ফোন নম্বর অথবা জন্মতারিখ মিলছে না"
                    return@addOnSuccessListener
                }

                val document = snap.documents.first()

                document.reference.update(
                    mapOf(
                        "email" to user.email,
                        "uid" to user.uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {

                    busy = false
                    studentId = document.id

                    getPreferences(MODE_PRIVATE).edit()
                        .putString("student_id", document.id)
                        .apply()

                    studentName = document.getString("name") ?: "Student"
                    message = ""
                    page = "dashboard"

                }.addOnFailureListener {
                    busy = false
                    message = it.message ?: "Verification ব্যর্থ"
                }
            }
            .addOnFailureListener {
                busy = false
                message = it.message ?: "Verification ব্যর্থ"
            }
    }

    private fun logout() {
        auth.signOut()
        GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.DEFAULT_SIGN_IN
        ).signOut()

        getPreferences(MODE_PRIVATE).edit()
            .remove("student_id")
            .apply()

        studentId = ""
        studentName = "Student"
        message = "Logout সফল হয়েছে"
        page = "welcome"
    }

    @Composable
    private fun AppScreen() {
        when (page) {
            "welcome" -> WelcomeScreen(
                onLogin = { page = "login" },
                onSignup = { page = "signup" }
            )

            "login" -> LoginScreen(
                message, busy,
                onLogin = { e, p -> login(e, p) },
                onGoogle = { googleLogin() },
                onSignup = { page = "signup" }
            )

            "signup" -> SignupScreen(
                message, busy,
                onSignup = { n, ph, d, e, p, ed, st ->
                    signup(n, ph, d, e, p, ed, st)
                },
                onGoogle = { googleLogin() },
                onLogin = { page = "login" }
            )

            "verify" -> VerifyScreen(
                message, busy,
                onVerify = { ph, d -> verifyStudent(ph, d) },
                onLogout = { logout() }
            )

            "dashboard" -> DashboardScreen(
                studentName,
                onLogout = { logout() }
            )
        }
    }
}

@Composable
private fun TargetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Navy,
            background = Bg,
            surface = Color.White
        ),
        content = content
    )
}

@Composable
private fun Page(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(35.dp))
        Text("TT", color = Color.White, fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Navy, RoundedCornerShape(50))
                .padding(horizontal = 23.dp, vertical = 13.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("TARGET TUTORIAL", fontSize = 23.sp,
            fontWeight = FontWeight.Bold, color = Navy)
        Text(subtitle, color = Blue, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            ) {
                Text(title, fontSize = 23.sp,
                    fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    if (text.isNotBlank()) {
        Text(text, color = Color(0xFFB3261E), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MainButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(13.dp)
    ) {
        Text(if (busy) "অপেক্ষা করো..." else text, fontSize = 16.sp)
    }
}

@Composable
private fun WelcomeScreen(
    onLogin: () -> Unit,
    onSignup: () -> Unit
) {
    Page("স্বাগতম!", "Learn • Practice • Succeed") {
        Text("তোমার পড়াশোনা, পরীক্ষা ও শিক্ষাসামগ্রী এক জায়গায়।",
            color = Navy, fontSize = 16.sp)
        Spacer(Modifier.height(25.dp))
        MainButton("Login", false, onLogin)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSignup,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("নতুন Account তৈরি করো")
        }
        Spacer(Modifier.height(15.dp))
        Text("Study Materials • Online Exam • Results • Notices",
            color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
private fun LoginScreen(
    message: String,
    busy: Boolean,
    onLogin: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onSignup: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Page("Student Login", "তোমার Account-এ প্রবেশ করো") {
        Message(message)
        Field("Email Address", email) { email = it }
        Field("Password", password) { password = it }
        MainButton("Login", busy) { onLogin(email.trim(), password) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onGoogle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Google দিয়ে Login") }
        TextButton(onClick = onSignup) {
            Text("নতুন শিক্ষার্থী? Sign Up করো")
        }
    }
}

@Composable
private fun SignupScreen(
    message: String,
    busy: Boolean,
    onSignup: (String, String, String, String, String, String, String) -> Unit,
    onGoogle: () -> Unit,
    onLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var education by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Page("Student Sign Up", "নতুন Account তৈরি করো") {
        Message(message)
        Field("Full Name", name) { name = it }
        Field("WhatsApp / Phone Number", phone) { phone = it }
        Field("Date of Birth (YYYY-MM-DD)", dob) { dob = it }
        Field("Email Address", email) { email = it }
        Field("Password (কমপক্ষে ৬ অক্ষর)", password) { password = it }
        Field("Education", education) { education = it }
        Field("Status", status) { status = it }

        MainButton("Sign Up", busy) {
            onSignup(name, phone, dob, email, password, education, status)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onGoogle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Google দিয়ে Sign Up") }
        TextButton(onClick = onLogin) {
            Text("আগে Account আছে? Login করো")
        }
    }
}

@Composable
private fun VerifyScreen(
    message: String,
    busy: Boolean,
    onVerify: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }

    Page("Student Verification", "নিজের Student Record যাচাই করো") {
        Message(message)
        Text(
            "ওয়েবসাইটে দেওয়া ফোন নম্বর ও জন্মতারিখ হুবহু লিখবে।",
            color = Color.Gray, fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Field("Phone Number", phone) { phone = it }
        Field("Date of Birth (YYYY-MM-DD)", dob) { dob = it }
        MainButton("Verify Student", busy) {
            onVerify(phone.trim(), dob.trim())
        }
        TextButton(onClick = onLogout) {
            Text("Logout")
        }
    }
}

@Composable
private fun DashboardScreen(
    name: String,
    onLogout: () -> Unit
) {
    Page("স্বাগতম, $name", "Student Dashboard") {
        Text("তোমার Student Account সফলভাবে যুক্ত হয়েছে।",
            color = Navy, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))

        listOf(
            "📚 Study Materials",
            "📝 Online Examination",
            "📊 Results",
            "📢 Notice Board",
            "👤 Student Profile",
            "💳 Payment Information"
        ).forEach {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEAF1FF)
                )
            ) {
                Text(it, modifier = Modifier.padding(17.dp),
                    color = Navy, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}
