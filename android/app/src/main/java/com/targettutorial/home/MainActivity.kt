package com.targettutorial.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

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
    private var isGuest by mutableStateOf(false)
    private var isVerified by mutableStateOf(false)
    private var moduleKey by mutableStateOf("materials")

    private val prefs by lazy {
        getSharedPreferences("target_tutorial", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(11, 31, 58)
        window.navigationBarColor = android.graphics.Color.rgb(11, 31, 58)

        isGuest = prefs.getBoolean("guest_mode", false)
        studentId = prefs.getString("student_id", "") ?: ""
        isVerified = studentId.isNotBlank()

        when {
            auth.currentUser != null -> {
                isGuest = false
                page = "dashboard"
                loadUserName()
                restoreStudentVerification(auth.currentUser!!)
            }

            isGuest -> {
                page = "dashboard"
                studentName = "Guest Student"
            }

            else -> page = "welcome"
        }

        setContent {
            TargetTheme {
                AppScreen()
            }
        }
    }

    private fun loadUserName() {
        val user = auth.currentUser ?: return

        db.collection("users")
            .whereEqualTo("uid", user.uid)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    studentName =
                        result.documents.first().getString("name")
                            ?: user.displayName
                            ?: "Student"
                } else {
                    studentName = user.displayName ?: "Student"
                }
            }
    }

    private fun enterGuestMode() {
        isGuest = true
        isVerified = false
        studentId = ""
        studentName = "Guest Student"

        prefs.edit()
            .putBoolean("guest_mode", true)
            .remove("student_id")
            .apply()

        message = ""
        page = "dashboard"
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
        if (
            name.isBlank() || phone.isBlank() ||
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

                val user = result.user ?: return@addOnSuccessListener

                val data = hashMapOf(
                    "uid" to user.uid,
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
                        isGuest = false
                        isVerified = false
                        studentId = ""
                        studentName = name

                        prefs.edit()
                            .putBoolean("guest_mode", false)
                            .remove("student_id")
                            .apply()

                        message = "Account তৈরি হয়েছে। চাইলে পরে Student Verification করতে পারবে।"
                        page = "dashboard"
                    }
                    .addOnFailureListener {
                        busy = false
                        isGuest = false
                        studentName = name
                        prefs.edit().putBoolean("guest_mode", false).apply()
                        message = "Account তৈরি হয়েছে, কিন্তু Profile তথ্য সেভ হয়নি: ${it.message}"
                        page = "dashboard"
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
                isGuest = false
                message = ""
                loadUserName()
                auth.currentUser?.let { restoreStudentVerification(it) }
                page = "dashboard"
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

                        val profile = mapOf(
                            "uid" to user.uid,
                            "name" to user.displayName,
                            "email" to user.email,
                            "google" to true
                        )

                        db.collection("users")
                            .whereEqualTo("uid", user.uid)
                            .get()
                            .addOnSuccessListener { snap ->

                                val saveTask = if (snap.isEmpty) {
                                    db.collection("users").add(
                                        profile + mapOf(
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                } else {
                                    db.collection("users")
                                        .document(snap.documents.first().id)
                                        .update(profile)
                                }

                                saveTask
                                    .addOnSuccessListener {
                                        busy = false
                                        isGuest = false
                                        studentName = user.displayName ?: "Student"
                                        prefs.edit().putBoolean("guest_mode", false).apply()
                                        restoreStudentVerification(user)
                                        message = ""
                                        page = "dashboard"
                                    }
                                    .addOnFailureListener {
                                        busy = false
                                        message = "Profile সেভ হয়নি: ${it.message}"
                                        page = "dashboard"
                                    }
                            }
                            .addOnFailureListener {
                                busy = false
                                message = it.message ?: "Profile পাওয়া যায়নি"
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

    // Website student-dashboard.html-এর মতো Login Email দিয়ে students Collection খোঁজে।
    private fun restoreStudentVerification(user: com.google.firebase.auth.FirebaseUser) {
        val email = user.email?.trim().orEmpty()
        if (email.isBlank()) return

        db.collection("students")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val student = snap.documents.first()
                    studentId = student.id
                    studentName = student.getString("name") ?: user.displayName ?: "Student"
                    isVerified = true
                    isGuest = false
                    prefs.edit()
                        .putBoolean("guest_mode", false)
                        .putString("student_id", student.id)
                        .apply()
                } else {
                    studentId = ""
                    isVerified = false
                    prefs.edit().remove("student_id").apply()
                }
            }
            .addOnFailureListener { error ->
                message = error.localizedMessage ?: "Student তথ্য যাচাই করা যায়নি।"
            }
    }

    
private fun verifyStudent(phone: String, dob: String) {
    val user = auth.currentUser

    if (user == null) {
        message = "প্রথমে Login করো।"
        page = "login"
        return
    }

    val cleanPhone = phone.trim()
    val cleanDob = dob.trim()

    if (cleanPhone.isBlank() || cleanDob.isBlank()) {
        message = "Phone ও Date of Birth দাও।"
        return
    }

    busy = true
    message = ""

    FirebaseFunctions.getInstance("us-central1")
        .getHttpsCallable("verifyStudent")
        .call(
            mapOf(
                "phone" to cleanPhone,
                "dob" to cleanDob
            )
        )
        .addOnSuccessListener { result ->

            val data = result.data as? Map<*, *>
            val success = data?.get("success") as? Boolean ?: false
            val sid = data?.get("studentId") as? String ?: ""
            val name = data?.get("name") as? String ?: "Student"

            if (success && sid.isNotBlank()) {
                studentId = sid
                studentName = name
                isVerified = true
                isGuest = false

                prefs.edit()
                    .putBoolean("guest_mode", false)
                    .putString("student_id", sid)
                    .apply()

                message = "Student Verification সফল হয়েছে।"
                page = "dashboard"
            } else {
                message = "Student Verification সফল হয়নি।"
            }

            busy = false
        }
        .addOnFailureListener { error ->
            busy = false
            message = error.localizedMessage ?: "Verification ব্যর্থ হয়েছে।"
        }
}


    private fun logout() {
        auth.signOut()

        GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.DEFAULT_SIGN_IN
        ).signOut()

        prefs.edit().clear().apply()

        isGuest = false
        isVerified = false
        studentId = ""
        studentName = "Student"
        message = ""
        page = "welcome"
    }

    @Composable
    private fun AppScreen() {
        when (page) {
            "welcome" -> WelcomeScreen(
                onLogin = { message = ""; page = "login" },
                onSignup = { message = ""; page = "signup" },
                onGuest = { enterGuestMode() }
            )

            "login" -> LoginScreen(
                message, busy,
                onLogin = { e, p -> login(e, p) },
                onGoogle = { googleLogin() },
                onSignup = { message = ""; page = "signup" },
                onGuest = { enterGuestMode() }
            )

            "signup" -> SignupScreen(
                message, busy,
                onSignup = { n, ph, d, e, p, ed, st ->
                    signup(n, ph, d, e, p, ed, st)
                },
                onGoogle = { googleLogin() },
                onLogin = { message = ""; page = "login" },
                onGuest = { enterGuestMode() }
            )

            "verify" -> VerifyScreen(
                message, busy,
                onVerify = { ph, d -> verifyStudent(ph, d) },
                onBack = { page = "dashboard" }
            )

            "dashboard" -> DashboardScreen(
                studentName,
                isGuest,
                isVerified,
                message,
                onVerify = {
                    if (isGuest) {
                        message = "Student Verification করতে আগে Login করো।"
                        page = "login"
                    } else {
                        message = ""
                        page = "verify"
                    }
                },
                onModule = { key ->
                    moduleKey = key
                    page = "module"
                },
                onLogout = { logout() }
            )

            "module" -> ModuleScreen(
                moduleKey = moduleKey,
                guest = isGuest,
                verified = isVerified,
                studentId = studentId,
                userUid = auth.currentUser?.uid.orEmpty(),
                onBack = { page = "dashboard" },
                onLogin = {
                    message = "এই সুবিধাটি ব্যবহার করতে Login করো।"
                    page = "login"
                }
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
        Spacer(Modifier.height(25.dp))

        Text(
            text = "TT",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Navy, RoundedCornerShape(50))
                .padding(horizontal = 23.dp, vertical = 13.dp)
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "TARGET TUTORIAL",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Text(text = subtitle, color = Blue, fontSize = 14.sp)

        Spacer(Modifier.height(25.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                Spacer(Modifier.height(18.dp))

                content()
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    if (text.isNotBlank()) {
        Text(text, color = Blue, fontSize = 13.sp)
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
private fun MainButton(
    text: String,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(13.dp)
    ) {
        Text(
            if (busy) "অপেক্ষা করো..." else text,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun WelcomeScreen(
    onLogin: () -> Unit,
    onSignup: () -> Unit,
    onGuest: () -> Unit
) {
    Page("স্বাগতম!", "Learn • Practice • Succeed") {
        Text(
            "তোমার পড়াশোনা, পরীক্ষা ও শিক্ষাসামগ্রী এক জায়গায়।",
            color = Navy,
            fontSize = 16.sp
        )

        Spacer(Modifier.height(24.dp))

        MainButton("Login", onClick = onLogin)

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onSignup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("নতুন Account তৈরি করো")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onGuest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guest হিসেবে প্রবেশ করো")
        }
    }
}

@Composable
private fun LoginScreen(
    message: String,
    busy: Boolean,
    onLogin: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onSignup: () -> Unit,
    onGuest: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Page("Student Login", "তোমার Account-এ প্রবেশ করো") {
        Message(message)

        Field("Email Address", email) { email = it }
        Field("Password", password) { password = it }

        MainButton("Login", busy) {
            onLogin(email.trim(), password)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onGoogle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Google দিয়ে Login")
        }

        TextButton(onClick = onSignup) {
            Text("নতুন শিক্ষার্থী? Sign Up করো")
        }

        TextButton(onClick = onGuest) {
            Text("Guest হিসেবে প্রবেশ করো")
        }
    }
}

@Composable
private fun SignupScreen(
    message: String,
    busy: Boolean,
    onSignup: (
        String, String, String, String,
        String, String, String
    ) -> Unit,
    onGoogle: () -> Unit,
    onLogin: () -> Unit,
    onGuest: () -> Unit
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
        Field("Password (কমপক্ষে ৬ অক্ষর)", password) {
            password = it
        }
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
        ) {
            Text("Google দিয়ে Sign Up")
        }

        TextButton(onClick = onLogin) {
            Text("আগে Account আছে? Login করো")
        }

        TextButton(onClick = onGuest) {
            Text("Guest হিসেবে প্রবেশ করো")
        }
    }
}

@Composable
private fun VerifyScreen(
    message: String,
    busy: Boolean,
    onVerify: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }

    Page("Student Verification", "এটি ঐচ্ছিক") {
        Message(message)

        Text(
            "Student Record-এর বিশেষ সুবিধা পেতে ওয়েবসাইটে দেওয়া ফোন নম্বর ও জন্মতারিখ মিলিয়ে যাচাই করো।",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(12.dp))

        Field("Phone Number", phone) { phone = it }
        Field("Date of Birth (YYYY-MM-DD)", dob) { dob = it }

        MainButton("Verify Student", busy) {
            onVerify(phone.trim(), dob.trim())
        }

        TextButton(onClick = onBack) {
            Text("পরে করব — Main Page-এ ফিরে যাও")
        }
    }
}

@Composable
private fun DashboardScreen(
    name: String,
    guest: Boolean,
    verified: Boolean,
    message: String,
    onVerify: () -> Unit,
    onModule: (String) -> Unit,
    onLogout: () -> Unit
) {
    val displayName = name.ifBlank { if (guest) "Guest Student" else "Student" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Navy, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TARGET TUTORIAL HOME", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("A Step Towards Success", color = Color(0xFFB9D4FF), fontSize = 11.sp)
                }
                Text("TT", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp))
            }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("👨‍🎓", fontSize = 31.sp, modifier = Modifier.background(Color(0xFF243F67), RoundedCornerShape(50)).padding(12.dp))
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Welcome back,", color = Color(0xFFB9D4FF), fontSize = 12.sp)
                    Text(displayName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (guest) "Guest Account" else if (verified) "Verified Student" else "Student Account",
                        color = Color(0xFFB9D4FF), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.background(Color(0xFF243F67), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(if (verified) "✓" else "★", color = Color.White, fontSize = 18.sp)
                    Text(if (verified) "Verified" else "Welcome", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 17.dp)) {
            if (message.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE5EEFF))) {
                    Text(message, color = Navy, fontSize = 12.sp, modifier = Modifier.padding(13.dp))
                }
                Spacer(Modifier.height(14.dp))
            }
            Text("Your Dashboard", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("পড়াশোনা ও পরীক্ষার সবকিছু এক জায়গায়", color = Color(0xFF718096), fontSize = 12.sp)
            Spacer(Modifier.height(15.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Blue)) {
                Row(modifier = Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("READY TO LEARN?", color = Color(0xFFDCE8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Online Examination", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("নিজের প্রস্তুতি যাচাই করো", color = Color.White, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onModule("exam") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("পরীক্ষা শুরু করো", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Text("📝", fontSize = 47.sp)
                }
            }

            Spacer(Modifier.height(21.dp))
            Text("Learning Services", color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(11.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceTile("📚", "Study", "Materials", Modifier.weight(1f)) { onModule("materials") }
                ServiceTile("📝", "Online", "Exam", Modifier.weight(1f)) { onModule("exam") }
                ServiceTile("📊", "Exam", "Results", Modifier.weight(1f)) { onModule("results") }
                ServiceTile("📢", "Notice", "Board", Modifier.weight(1f)) { onModule("notices") }
            }
            Spacer(Modifier.height(9.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceTile("📖", "Class", "Notes", Modifier.weight(1f)) { onModule("notes") }
                ServiceTile("📄", "Assignments", "", Modifier.weight(1f)) { onModule("assignments") }
                ServiceTile("💳", "Payment", "", Modifier.weight(1f)) { onModule("payments") }
                ServiceTile("👤", "My", "Profile", Modifier.weight(1f)) { onModule("profile") }
            }

            Spacer(Modifier.height(19.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(15.dp)) {
                    Text("📌  Student Verification", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(when {
                        verified -> "তোমার Student Verification সম্পন্ন হয়েছে।"
                        guest -> "Student হিসেবে যুক্ত হতে Login করে Verification করতে পারো।"
                        else -> "Student-এর অতিরিক্ত সুবিধা পেতে চাইলে যাচাই সম্পন্ন করো। এটি ঐচ্ছিক।"
                    }, color = Color(0xFF718096), fontSize = 11.sp)
                    if (!verified) {
                        Spacer(Modifier.height(9.dp))
                        OutlinedButton(onClick = onVerify, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Text(if (guest) "Login / Student Verification" else "Verify Student")
                        }
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (guest) "Guest Mode বন্ধ করো" else "Logout")
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            BottomNavItem("⌂", "Home", true) { }
            BottomNavItem("▣", "Exam", false) { onModule("exam") }
            BottomNavItem("▥", "Progress", false) { onModule("results") }
            BottomNavItem("♙", "Profile", false) { onModule("profile") }
        }
    }
}
@Composable
private fun ServiceTile(
    emoji: String,
    firstLine: String,
    secondLine: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.height(91.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        val tileContent: @Composable ColumnScope.() -> Unit = {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                firstLine,
                color = Navy,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (secondLine.isNotBlank()) {
                Text(secondLine, color = Color(0xFF718096), fontSize = 9.sp, maxLines = 1)
            }
        }

        if (onClick != null) {
            TextButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    content = tileContent
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                content = tileContent
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                icon,
                color = if (selected) Blue else Color(0xFF8491A5),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                color = if (selected) Navy else Color(0xFF8491A5),
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ModuleScreen(
    moduleKey: String,
    guest: Boolean,
    verified: Boolean,
    studentId: String,
    userUid: String,
    onBack: () -> Unit,
    onLogin: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val title = when (moduleKey) {
        "exam" -> "Online Examination"
        "materials" -> "Study Materials"
        "notes" -> "Class Notes"
        "results" -> "Exam Results & Progress"
        "notices" -> "Notice Board"
        "assignments" -> "Assignments"
        "payments" -> "Payment Information"
        "profile" -> "Student Profile"
        else -> "Student Module"
    }

    if (moduleKey == "exam") {
        val url = "https://nibedansarkar12.github.io/targettutorialhome/quiz.html"
        Column(Modifier.fillMaxSize().background(Bg)) {
            Row(Modifier.fillMaxWidth().background(Navy).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← ফিরে যাও", color = Color.White) }
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    var tokenRequested = false
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.allowFileAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            settings.forceDark = WebSettings.FORCE_DARK_OFF
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            settings.isAlgorithmicDarkeningAllowed = false
                        }
                        setBackgroundColor(android.graphics.Color.WHITE)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, pageUrl: String) {
                                super.onPageFinished(view, pageUrl)
                                if (!pageUrl.startsWith("https://nibedansarkar12.github.io/targettutorialhome/")) return
                                if (tokenRequested) return

                                val nativeUser = FirebaseAuth.getInstance().currentUser
                                if (nativeUser == null) return
                                tokenRequested = true

                                FirebaseFunctions.getInstance("us-central1")
                                    .getHttpsCallable("createExamWebToken")
                                    .call()
                                    .addOnSuccessListener { result ->
                                        val response = result.getData() as? Map<*, *>
                                        val customToken = response?.get("token") as? String
                                        if (customToken.isNullOrBlank()) {
                                            tokenRequested = false
                                            android.util.Log.e("TargetExam", "Custom token missing from callable response")
                                            return@addOnSuccessListener
                                        }
                                        val safeToken = org.json.JSONObject.quote(customToken)
                                        view.evaluateJavascript(
                                            "window.__signInWithNativeToken && window.__signInWithNativeToken($safeToken);",
                                            null
                                        )
                                    }
                                    .addOnFailureListener { error ->
                                        tokenRequested = false
                                        android.util.Log.e("TargetExam", "Could not create WebView session", error)
                                        view.evaluateJavascript(
                                            "window.__nativeSessionError = 'অ্যাপের Login সংযোগ করা যায়নি। ইন্টারনেট চালু করে আবার চেষ্টা করুন।';",
                                            null
                                        )
                                    }
                            }
                        }
                        loadUrl(url)
                    }
                },
                update = { view -> if (view.url.isNullOrBlank()) view.loadUrl(url) }
            )
        }
        return
    }

    val db = remember { FirebaseFirestore.getInstance() }
    var rows by remember(moduleKey, userUid, studentId) { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember(moduleKey, userUid, studentId) { mutableStateOf(true) }
    var error by remember(moduleKey, userUid, studentId) { mutableStateOf("") }

    LaunchedEffect(moduleKey, userUid, studentId, guest, verified) {
        rows = emptyList()
        error = ""
        loading = true

        when {
            moduleKey == "assignments" -> {
                // The checked Admin Panel currently has no assignment creation/submission section.
                loading = false
                error = "বর্তমান Admin Panel-এ Assignment যোগ বা জমা নেওয়ার ব্যবস্থা নেই। তাই এখানে দেখানোর মতো Assignment ডেটা নেই।"
            }
            guest && moduleKey in listOf("results", "profile", "payments") -> {
                loading = false
            }
            moduleKey in listOf("profile", "payments") && (!verified || studentId.isBlank()) -> {
                loading = false
                error = "এই তথ্য দেখতে আগে Student Verification সম্পন্ন করো।"
            }
            moduleKey == "results" && userUid.isBlank() -> {
                loading = false
            }
            else -> {
                if (moduleKey == "payments" || moduleKey == "profile") {
                    db.collection("students").document(studentId).get()
                        .addOnSuccessListener { snapshot ->
                            rows = if (snapshot.exists()) {
                                listOf((snapshot.data ?: emptyMap()) + mapOf("_documentId" to snapshot.id))
                            } else emptyList()
                            loading = false
                        }
                        .addOnFailureListener { exception ->
                            error = exception.localizedMessage ?: "পেমেন্ট ডেটা লোড করা যায়নি। Firebase Rules পরীক্ষা করো।"
                            loading = false
                        }
                } else {
                    val query = when (moduleKey) {
                        "materials", "notes" ->
                            db.collection("driveLinks").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        "notices" ->
                            db.collection("notices").whereEqualTo("active", true)
                        "results" ->
                            db.collection("results").whereEqualTo("uid", userUid)
                        else -> null
                    }

                    if (query == null) {
                        loading = false
                        error = "এই বিভাগটি এখনো সংযুক্ত করা হয়নি।"
                    } else {
                        query.get()
                            .addOnSuccessListener { snapshot ->
                                val docs = snapshot.documents.map { doc ->
                                    (doc.data ?: emptyMap()) + mapOf("_documentId" to doc.id)
                                }
                                rows = when (moduleKey) {
                                    "notices" -> docs.sortedByDescending { (it["createdAt"] as? Number)?.toLong() ?: 0L }
                                    "results" -> docs.sortedByDescending {
                                        when (val ts = it["submittedAt"]) {
                                            is com.google.firebase.Timestamp -> ts.toDate().time
                                            is Number -> ts.toLong()
                                            else -> 0L
                                        }
                                    }
                                    else -> docs
                                }
                                loading = false
                            }
                            .addOnFailureListener { exception ->
                                error = exception.localizedMessage ?: "ডেটা লোড করা যায়নি। Firebase Rules পরীক্ষা করো।"
                                loading = false
                            }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Navy).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← ফিরে যাও", color = Color.White) }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(title, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            when {
                guest && moduleKey in listOf("results", "profile", "payments") -> {
                    Text("এই ব্যক্তিগত বিভাগ দেখতে Login প্রয়োজন।", color = Navy)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onLogin) { Text("Login করো") }
                }
                loading -> CircularProgressIndicator()
                error.isNotBlank() -> {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text(error, color = Color(0xFF9B1C1C), modifier = Modifier.padding(16.dp))
                    }
                }
                rows.isEmpty() -> {
                    val emptyText = when (moduleKey) {
                        "materials", "notes" -> "Admin Panel → Manage Notes থেকে Google Drive link যোগ করলে এখানে দেখা যাবে।"
                        "notices" -> "এখন কোনো Active Notice নেই।"
                        "results" -> "তোমার কোনো পরীক্ষার ফলাফল এখনো পাওয়া যায়নি।"
                        "profile" -> "এই Login UID-এর সঙ্গে যুক্ত User Profile পাওয়া যায়নি।"
                        "payments" -> "এই Student ID-তে কোনো পেমেন্ট তথ্য পাওয়া যায়নি।"
                        else -> "এখনো কোনো তথ্য যোগ করা হয়নি।"
                    }
                    Text(emptyText, color = Color(0xFF526174), fontSize = 14.sp)
                }
                moduleKey == "payments" -> {
                    val data = rows.first()
                    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                    val paymentRows = months.mapNotNull { month ->
                        val value = data[month] ?: return@mapNotNull null
                        val amount: String
                        val date: String
                        if (value is Map<*, *>) {
                            amount = (value["amount"] ?: 0).toString()
                            date = (value["note"] ?: "—").toString()
                        } else {
                            amount = value.toString()
                            date = "—"
                        }
                        Triple(month, date, amount)
                    }
                    val total = paymentRows.sumOf { it.third.toDoubleOrNull() ?: 0.0 }
                    val lastPayDate = paymentRows.lastOrNull { it.second.isNotBlank() && it.second != "—" }?.second ?: "—"
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(15.dp)) {
                            Text("পেমেন্ট সারাংশ", color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("মোট Paid: ₹${total.toInt()}   |   Paid মাস: ${paymentRows.size}", color = Blue, fontWeight = FontWeight.Bold)
                            Text("শেষ পেমেন্ট: $lastPayDate", color = Color(0xFF64748B), fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            paymentRows.forEach { (month, date, amount) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(month, color = Navy, fontWeight = FontWeight.SemiBold)
                                    Text(date, color = Color(0xFF64748B), fontSize = 12.sp)
                                    Text("₹$amount", color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                                }
                                Divider(color = Color(0xFFE2E8F0))
                            }
                            if (paymentRows.isEmpty()) Text("এখনো কোনো পেমেন্ট নেই।", color = Color(0xFF64748B))
                        }
                    }
                }
                else -> {
                    rows.forEach { item ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                when (moduleKey) {
                                    "materials", "notes" -> {
                                        Text(item["title"]?.toString() ?: "Study Material", color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        val link = item["url"]?.toString()?.trim().orEmpty()
                                        if (link.isNotBlank()) {
                                            Spacer(Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                                    } catch (_: Exception) {
                                                        error = "এই লিংকটি খোলা যাচ্ছে না।"
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("📖 খুলুন / Download করুন") }
                                        } else {
                                            Text("এই Note-এর URL দেওয়া নেই।", color = Color(0xFFB42318), fontSize = 12.sp)
                                        }
                                    }
                                    "notices" -> {
                                        Text(item["title"]?.toString() ?: "Notice", color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Spacer(Modifier.height(5.dp))
                                        Text(item["message"]?.toString() ?: "বিস্তারিত নেই", color = Color(0xFF334155))
                                        Text("Priority: ${item["priority"] ?: "normal"}", color = Color(0xFF64748B), fontSize = 11.sp)
                                    }
                                    "results" -> {
                                        Text(item["exam"]?.toString() ?: "পরীক্ষার ফলাফল", color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        val correct = item["correct"]?.toString() ?: item["score"]?.toString() ?: "0"
                                        val total = item["total"]?.toString() ?: "0"
                                        Text("সঠিক উত্তর: $correct / $total", color = Color(0xFF334155))
                                        Text("ভুল: ${item["wrong"] ?: "—"}   |   না করা: ${item["unattempted"] ?: "—"}", color = Color(0xFF64748B), fontSize = 12.sp)
                                    }
                                    "profile" -> {
                                        val fields = listOf(
                                            "name" to "নাম",
                                            "phone" to "ফোন নম্বর",
                                            "dob" to "জন্মতারিখ",
                                            "father" to "পিতার নাম",
                                            "batch" to "ব্যাচ",
                                            "whatsapp" to "হোয়াটসঅ্যাপ",
                                            "email" to "ইমেইল"
                                        )
                                        fields.forEach { (key, label) ->
                                            val value = item[key]?.toString().orEmpty()
                                            if (value.isNotBlank()) {
                                                Text("$label: $value", color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

