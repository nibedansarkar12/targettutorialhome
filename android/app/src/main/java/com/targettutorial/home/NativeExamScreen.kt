package com.targettutorial.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

private data class NativeExamQuestion(
    val documentId: String,
    val question: String,
    val options: List<String>,
    val answer: String,
    val topic: String,
    val difficulty: String
)

private val ExamNavy = Color(0xFF0B1F3A)
private val ExamBlue = Color(0xFF2563EB)
private val ExamBg = Color(0xFFF1F5F9)

@Composable
internal fun NativeExamScreen(
    userUid: String,
    userEmail: String,
    displayName: String,
    onBack: () -> Unit,
    onLogin: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val categories = remember {
        listOf("class7", "class8", "class9", "class10", "class11", "class12")
    }
    var subcategories by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var questions by remember { mutableStateOf<List<NativeExamQuestion>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedExam by remember { mutableStateOf("") }
    var selectedExamTitle by remember { mutableStateOf("") }
    var answers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var reviewed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("categories") }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    fun loadSubcategories(category: String) {
        selectedCategory = category
        loading = true
        error = ""
        subcategories = emptyList()
        db.collection("subcategories")
            .whereEqualTo("category", category)
            .get()
            .addOnSuccessListener { snapshot ->
                subcategories = snapshot.documents.map { doc ->
                    (doc.getString("name") ?: doc.id) to (doc.getString("id") ?: doc.id)
                }
                loading = false
                screen = "subcategories"
                if (subcategories.isEmpty()) error = "এই শ্রেণির কোনো Subcategory পাওয়া যায়নি।"
            }
            .addOnFailureListener {
                loading = false
                error = it.localizedMessage ?: "Subcategory লোড করা যায়নি।"
            }
    }

    fun startExam(examId: String, title: String) {
        if (userUid.isBlank()) {
            onLogin()
            return
        }
        loading = true
        error = ""
        db.collection(examId).get()
            .addOnSuccessListener { snapshot ->
                val loaded = snapshot.documents
                    .sortedBy { it.getLong("id") ?: Long.MAX_VALUE }
                    .map { doc ->
                        val rawOptions = doc.get("options") as? List<*>
                        NativeExamQuestion(
                            documentId = doc.id,
                            question = doc.getString("question") ?: doc.getString("title") ?: "",
                            options = rawOptions?.mapNotNull { it?.toString() } ?: emptyList(),
                            answer = doc.get("answer")?.toString() ?: "",
                            topic = doc.getString("topic") ?: "General",
                            difficulty = doc.getString("difficulty") ?: "-"
                        )
                    }
                    .filter { it.question.isNotBlank() }
                loading = false
                if (loaded.isEmpty()) {
                    error = "এই পরীক্ষার জন্য কোনো প্রশ্ন পাওয়া যায়নি।"
                    return@addOnSuccessListener
                }
                questions = loaded
                selectedExam = examId
                selectedExamTitle = title
                answers = emptyMap()
                reviewed = emptySet()
                currentIndex = 0
                secondsLeft = loaded.size * 60
                screen = "quiz"
            }
            .addOnFailureListener {
                loading = false
                error = it.localizedMessage ?: "প্রশ্ন লোড করা যায়নি।"
            }
    }

    fun submitExam() {
        if (submitting || questions.isEmpty()) return
        submitting = true
        var correct = 0
        var wrong = 0
        var unattempted = 0
        val topicMap = mutableMapOf<String, Int>()
        questions.forEachIndexed { index, question ->
            val given = answers[index]
            if (given.isNullOrBlank()) {
                unattempted++
            } else if (given.trim() == question.answer.trim()) {
                correct++
            } else {
                wrong++
                topicMap[question.topic] = (topicMap[question.topic] ?: 0) + 1
            }
        }
        val stats = mapOf(
            "correct" to correct,
            "wrong" to wrong,
            "unattempted" to unattempted,
            "attempted" to (correct + wrong),
            "total" to questions.size,
            "percent" to if (questions.isEmpty()) 0 else (correct * 100 / questions.size)
        )
        result = stats
        val payload = hashMapOf<String, Any?>(
            "uid" to userUid,
            "email" to userEmail,
            "displayName" to displayName,
            "exam" to selectedExam,
            "score" to correct,
            "total" to questions.size,
            "correct" to correct,
            "wrong" to wrong,
            "unattempted" to unattempted,
            "topicMap" to topicMap,
            "submittedAt" to FieldValue.serverTimestamp()
        )
        db.collection("results").add(payload)
            .addOnCompleteListener {
                submitting = false
                screen = "result"
            }
    }

    LaunchedEffect(screen, selectedExam) {
        if (screen == "quiz") {
            while (secondsLeft > 0 && screen == "quiz") {
                delay(1000)
                secondsLeft--
            }
            if (secondsLeft <= 0 && screen == "quiz") submitExam()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ExamBg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(ExamNavy).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                when (screen) {
                    "categories" -> onBack()
                    "subcategories" -> { screen = "categories"; error = "" }
                    "quiz" -> { screen = "subcategories"; questions = emptyList() }
                    "result" -> { screen = "categories"; questions = emptyList() }
                }
            }) { Text("← ফিরে যান", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text("নিজস্ব পরীক্ষা ব্যবস্থা", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (loading || submitting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFB91C1C), modifier = Modifier.padding(16.dp))
        }

        when (screen) {
            "categories" -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("পরীক্ষার শ্রেণি নির্বাচন করুন", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ExamNavy)
                    Text("Class 7 থেকে Class 12 পর্যন্ত পরীক্ষা", color = Color.Gray, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
                    categories.forEach { category ->
                        ExamMenuCard(category.uppercase(), "বিষয় ও Subcategory দেখতে চাপ দিন") {
                            loadSubcategories(category)
                        }
                    }
                }
            }
            "subcategories" -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("${selectedCategory.uppercase()} — বিষয় নির্বাচন", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ExamNavy)
                    if (subcategories.isEmpty() && !loading && error.isBlank()) {
                        Text("কোনো Subcategory পাওয়া যায়নি।", modifier = Modifier.padding(12.dp))
                    }
                    subcategories.forEach { (name, id) ->
                        ExamMenuCard(name, "MCQ Practice • পরীক্ষা শুরু করুন") {
                            startExam("${selectedCategory}__${id}", name)
                        }
                    }
                }
            }
            "quiz" -> {
                val q = questions.getOrNull(currentIndex)
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedExamTitle, fontWeight = FontWeight.Bold, color = ExamNavy, fontSize = 18.sp)
                            Text("প্রশ্ন ${currentIndex + 1} / ${questions.size}", color = Color.Gray)
                        }
                        Surface(color = Color(0xFFE0E7FF), shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = ExamNavy, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if (questions.isEmpty()) 0f else answers.size.toFloat() / questions.size },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                    if (q != null) {
                        Card(Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                Text("প্রশ্ন ${currentIndex + 1}", color = ExamBlue, fontWeight = FontWeight.Bold)
                                Text(q.question, color = ExamNavy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 14.dp))
                                Text("Difficulty: ${q.difficulty}", color = Color.Gray, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                q.options.forEachIndexed { optionIndex, option ->
                                    val chosen = answers[currentIndex] == option
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                            answers = answers + (currentIndex to option)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (chosen) Color(0xFFDBEAFE) else Color(0xFFF8FAFC),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (chosen) ExamBlue else Color(0xFFCBD5E1))
                                    ) {
                                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = chosen, onClick = { answers = answers + (currentIndex to option) })
                                            Text("${('A'.code + optionIndex).toChar()}. $option", color = ExamNavy, modifier = Modifier.padding(start = 8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { if (currentIndex > 0) currentIndex-- }, enabled = currentIndex > 0) { Text("পূর্ববর্তী") }
                        TextButton(onClick = {
                            reviewed = if (currentIndex in reviewed) reviewed - currentIndex else reviewed + currentIndex
                        }) { Text(if (currentIndex in reviewed) "★ মার্ক সরান" else "☆ মার্ক") }
                        Button(onClick = { if (currentIndex < questions.lastIndex) currentIndex++ else showSubmitDialog = true }) {
                            Text(if (currentIndex < questions.lastIndex) "পরবর্তী" else "জমা দিন")
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("প্রশ্ন তালিকা", color = ExamNavy, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            val chunks = questions.indices.toList().chunked(6)
                            chunks.forEach { chunk ->
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    chunk.forEach { index ->
                                        val bg = when {
                                            index == currentIndex -> ExamBlue
                                            index in reviewed -> Color(0xFFF59E0B)
                                            answers.containsKey(index) -> Color(0xFF059669)
                                            else -> Color(0xFFE2E8F0)
                                        }
                                        Box(
                                            Modifier.weight(1f).height(38.dp)
                                                .background(bg, RoundedCornerShape(8.dp))
                                                .clickable { currentIndex = index },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${index + 1}", color = if (bg == Color(0xFFE2E8F0)) ExamNavy else Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    repeat(6 - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            Text("সবুজ: উত্তর দেওয়া • হলুদ: Review • নীল: বর্তমান", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (showSubmitDialog) {
                    AlertDialog(
                        onDismissRequest = { showSubmitDialog = false },
                        title = { Text("পরীক্ষা জমা দেবেন?") },
                        text = { Text("উত্তর দেওয়া হয়েছে ${answers.size}টি / ${questions.size}টি। জমা দেওয়ার পর আর পরিবর্তন করা যাবে না।") },
                        confirmButton = {
                            TextButton(onClick = { showSubmitDialog = false; submitExam() }) { Text("হ্যাঁ, জমা দিন") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSubmitDialog = false }) { Text("ফিরে যান") }
                        }
                    )
                }
            }
            "result" -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    Text("পরীক্ষার ফলাফল", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ExamNavy)
                    Text(selectedExamTitle, color = Color.Gray, modifier = Modifier.padding(bottom = 14.dp))
                    val total = result["total"] ?: 0
                    val correct = result["correct"] ?: 0
                    val wrong = result["wrong"] ?: 0
                    val unattempted = result["unattempted"] ?: 0
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${result["percent"] ?: 0}%", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = ExamBlue)
                            Text("স্কোর: $correct / $total", color = ExamNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    listOf("সঠিক উত্তর" to correct, "ভুল উত্তর" to wrong, "উত্তর দেওয়া হয়নি" to unattempted).forEach { (label, value) ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, color = ExamNavy)
                                Text("$value", color = ExamNavy, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(onClick = { screen = "categories"; questions = emptyList(); error = "" }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("পরীক্ষার তালিকায় ফিরে যান")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamMenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(12.dp)) {
                Text("📝", modifier = Modifier.padding(12.dp), fontSize = 22.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, color = ExamNavy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Text("›", color = ExamBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}
