package com.targettutorial.home

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File

private val IdNavy = Color(0xFF08244B)
private val IdBlue = Color(0xFF174A91)
private val IdGold = Color(0xFFE5B94F)
private val IdPale = Color(0xFFF1F6FF)
private val IdInk = Color(0xFF132B4D)

@Composable
fun StudentIdCardScreen(
    studentName: String,
    studentId: String,
    className: String = "",
    batch: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var displayName by remember(studentId, studentName) { mutableStateOf(studentName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var classLabel by remember(studentId, className) { mutableStateOf(className) }
    var batchLabel by remember(studentId, batch) { mutableStateOf(batch) }
    var photoBitmap by remember(studentId) { mutableStateOf(loadLocalPhoto(context, studentId)) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    val signatureBitmap = remember {
        android.graphics.BitmapFactory.decodeResource(
            context.resources,
            R.drawable.n_sarkar_signature
        )
    }

    // MainActivity passes the Firestore students document ID as studentId.
    // Read students/{studentId}, then resolve its batch ID through batches/{batchId}.
    LaunchedEffect(studentId) {
        loading = true
        if (studentId.isBlank()) {
            status = "Student verification না থাকায় তথ্য পাওয়া যায়নি।"
            loading = false
        } else {
            db.collection("students").document(studentId).get()
                .addOnSuccessListener { studentDoc ->
                    if (!studentDoc.exists()) {
                        status = "students/$studentId রেকর্ড পাওয়া যায়নি।"
                        loading = false
                        return@addOnSuccessListener
                    }

                    displayName = firstValue(
                        studentDoc.getString("name"),
                        studentName
                    )
                    phone = firstValue(
                        studentDoc.getString("phone"),
                        studentDoc.getString("phoneNumber"),
                        studentDoc.getString("mobile"),
                        studentDoc.getString("whatsapp")
                    )
                    address = firstValue(
                        studentDoc.getString("address"),
                        studentDoc.getString("fullAddress"),
                        studentDoc.getString("village")
                    )

                    // Prefer an explicit class field. The screenshot showed education empty,
                    // so batch document name is also checked for a "Class 7" style label.
                    val explicitClass = firstValue(
                        studentDoc.getString("class"),
                        studentDoc.getString("className"),
                        studentDoc.getString("studentClass"),
                        studentDoc.getString("education"),
                        className
                    )
                    classLabel = explicitClass

                    val batchId = firstValue(
                        studentDoc.getString("batch"),
                        studentDoc.getString("batchId")
                    )

                    if (batchId.isBlank()) {
                        batchLabel = firstValue(batch, "Batch not assigned")
                        if (classLabel.isBlank()) classLabel = "Class not set"
                        loading = false
                    } else {
                        db.collection("batches").document(batchId).get()
                            .addOnSuccessListener { batchDoc ->
                                val actualBatchName = firstValue(
                                    batchDoc.getString("name"),
                                    batchDoc.getString("batchName"),
                                    batchDoc.getString("title")
                                )
                                batchLabel = actualBatchName.ifBlank { "Batch name unavailable" }

                                // If no separate class field exists but the batch name itself
                                // is "Class 7", use that as the class label too.
                                if (classLabel.isBlank()) {
                                    val classFromBatch = Regex(
                                        """(?i)\bclass\s*[-:]?\s*(\d{1,2})\b"""
                                    ).find(actualBatchName)?.groupValues?.getOrNull(1)
                                    classLabel = classFromBatch?.let { "Class $it" }
                                        ?: "Class not set"
                                }
                                loading = false
                            }
                            .addOnFailureListener { e ->
                                batchLabel = "Batch name unavailable"
                                if (classLabel.isBlank()) classLabel = "Class not set"
                                status = "Batch তথ্য পড়া যায়নি: ${e.localizedMessage ?: "Permission denied"}"
                                loading = false
                            }
                    }
                }
                .addOnFailureListener { e ->
                    status = "Student তথ্য পড়া যায়নি: ${e.localizedMessage ?: "Permission denied"}"
                    loading = false
                }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val file = File(context.filesDir, "student_photo_${safeName(studentId)}.jpg")
                    file.outputStream().use { output -> input.copyTo(output) }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                } ?: error("ছবি খোলা যায়নি")
            }.onSuccess {
                photoBitmap = it
                status = if (it != null) "ছবি এই ফোনে সেভ হয়েছে।" else "ছবি পড়া যায়নি।"
            }.onFailure {
                status = "ছবি সেভ হয়নি: ${it.localizedMessage}"
            }
        }
    }

    val qrBitmap = remember(studentId) {
        runCatching { makeStudentQr(studentId.ifBlank { "UNVERIFIED" }) }.getOrNull()
    }

    Column(
        Modifier.fillMaxSize()
            .background(IdPale)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(
                "Digital Student ID",
                color = IdNavy,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // Portrait card preview: navy/gold header, centered portrait, details, QR and signature.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(IdNavy, RoundedCornerShape(22.dp))
                        .padding(vertical = 19.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TARGET TUTORIAL HOME",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "OFFICIAL DIGITAL STUDENT ID",
                        color = Color(0xFFD8E6FA),
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(0.55f).height(2.dp).background(IdGold))
                }

                Spacer(Modifier.height(17.dp))
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = "Student photo",
                        modifier = Modifier.size(142.dp)
                            .background(IdPale, RoundedCornerShape(24.dp))
                    )
                } else {
                    Box(
                        Modifier.size(142.dp)
                            .background(IdPale, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            studentInitials(displayName),
                            color = IdNavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 38.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    displayName.ifBlank { "Student Name" },
                    color = IdNavy,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Surface(color = IdBlue, shape = RoundedCornerShape(50)) {
                    Text(
                        "STUDENT ID: ${studentId.ifBlank { "Not verified" }}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .background(IdPale, RoundedCornerShape(18.dp))
                        .padding(13.dp)
                ) {
                    Text(
                        "STUDENT INFORMATION",
                        color = IdBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    StudentInfoLine("Phone", phone.ifBlank { "Not provided" })
                    StudentInfoLine("Address", address.ifBlank { "Not provided" })
                    StudentInfoLine("Class", classLabel.ifBlank { "Class not set" })
                    StudentInfoLine("Batch", batchLabel.ifBlank { "Batch not assigned" })
                }

                Spacer(Modifier.height(15.dp))
                qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Student ID QR",
                        modifier = Modifier.size(112.dp)
                            .background(Color.White, RoundedCornerShape(10.dp))
                    )
                }
                Text(
                    "SCAN TO VERIFY STUDENT ID",
                    color = IdBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFE2EAF5))
                Spacer(Modifier.height(10.dp))
                Text(
                    "TEACHER: NIBEDAN SARKAR",
                    color = IdNavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Image(
                    bitmap = signatureBitmap.asImageBitmap(),
                    contentDescription = "N Sarkar signature artwork",
                    modifier = Modifier.width(175.dp).height(58.dp)
                )
                Text(
                    "N. SARKAR  |  AUTHORIZED SIGNATORY",
                    color = IdNavy,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { photoPicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (photoBitmap == null) "Add Student Photo (Optional)" else "Change Photo")
        }

        Button(
            onClick = {
                val result = savePortraitIdPdf(
                    context, displayName, studentId, phone, address,
                    classLabel, batchLabel, qrBitmap, photoBitmap, signatureBitmap
                )
                status = result.fold(
                    { "PDF সেভ হয়েছে: $it" },
                    { "PDF সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = IdNavy)
        ) {
            Text("Download ID Card PDF")
        }

        OutlinedButton(
            onClick = {
                val result = savePortraitIdImage(
                    context, displayName, studentId, phone, address,
                    classLabel, batchLabel, qrBitmap, photoBitmap, signatureBitmap
                )
                status = result.fold(
                    { "ID Card Image Gallery-তে সেভ হয়েছে।" },
                    { "Image সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save ID Card Image")
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = IdBlue,
                strokeWidth = 2.dp
            )
            Text("Student ও Batch তথ্য লোড হচ্ছে…", color = IdBlue, fontSize = 11.sp)
        }
        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
        Text(
            "Student photo ও ID Card শুধু এই ফোনে সেভ হবে; Firebase Storage-এ আপলোড হবে না।",
            color = Color.Gray,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StudentInfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = Color(0xFF71829A),
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp)
        )
        Text(
            value,
            color = IdInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun firstValue(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

private fun makeStudentQr(value: String): Bitmap {
    val size = 420
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x, y,
                if (matrix.get(x, y)) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            )
        }
    }
    return bitmap
}

private fun loadLocalPhoto(context: Context, studentId: String): Bitmap? {
    if (studentId.isBlank()) return null
    val file = File(context.filesDir, "student_photo_${safeName(studentId)}.jpg")
    return if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath) else null
}

private fun savePortraitIdPdf(
    context: Context,
    name: String,
    id: String,
    phone: String,
    address: String,
    className: String,
    batchName: String,
    qr: Bitmap?,
    photo: Bitmap?,
    signature: Bitmap
): Result<String> = runCatching {
    val document = PdfDocument()
    try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(600, 900, 1).create())
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = android.graphics.Color.rgb(8, 36, 75)
        canvas.drawRoundRect(12f, 12f, 588f, 888f, 30f, 30f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(25f, 25f, 575f, 875f, 23f, 23f, paint)

        paint.color = android.graphics.Color.rgb(8, 36, 75)
        canvas.drawRoundRect(25f, 25f, 575f, 175f, 23f, 23f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 29f
        canvas.drawText("TARGET TUTORIAL HOME", 300f, 83f, paint)
        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("OFFICIAL DIGITAL STUDENT ID", 300f, 116f, paint)

        photo?.let {
            val scaled = Bitmap.createScaledBitmap(it, 160, 160, true)
            canvas.drawBitmap(scaled, 220f, 195f, paint)
        }
        paint.textAlign = Paint.Align.CENTER
        paint.color = android.graphics.Color.rgb(8, 36, 75)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 28f
        canvas.drawText(name.ifBlank { "Student Name" }.take(28), 300f, 395f, paint)

        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Student ID: ${id.ifBlank { "Not verified" }}".take(55), 300f, 428f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 18f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("STUDENT INFORMATION", 65f, 485f, paint)
        paint.textSize = 16f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Phone: ${phone.ifBlank { "Not provided" }}".take(55), 65f, 525f, paint)
        canvas.drawText("Address: ${address.ifBlank { "Not provided" }}".take(58), 65f, 562f, paint)
        canvas.drawText("Class: ${className.ifBlank { "Class not set" }}".take(55), 65f, 599f, paint)
        canvas.drawText("Batch: ${batchName.ifBlank { "Batch not assigned" }}".take(55), 65f, 636f, paint)

        qr?.let {
            canvas.drawBitmap(Bitmap.createScaledBitmap(it, 105, 105, true), 247f, 650f, paint)
        }
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f
        canvas.drawText("SCAN TO VERIFY", 300f, 770f, paint)

        canvas.drawBitmap(Bitmap.createScaledBitmap(signature, 180, 58, true), 210f, 775f, paint)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 13f
        canvas.drawText("TEACHER: NIBEDAN SARKAR", 300f, 846f, paint)
        canvas.drawText("N. SARKAR  |  AUTHORIZED SIGNATORY", 300f, 865f, paint)

        document.finishPage(page)

        val filename = "TargetTutorial_ID_${safeName(id)}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/TargetTutorial"
                )
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"), values
        ) ?: error("PDF ফাইল তৈরি করা যায়নি")
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            ?: error("PDF লেখা যায়নি")
        filename
    } finally {
        document.close()
    }
}

private fun savePortraitIdImage(
    context: Context,
    name: String,
    id: String,
    phone: String,
    address: String,
    className: String,
    batchName: String,
    qr: Bitmap?,
    photo: Bitmap?,
    signature: Bitmap
): Result<String> = runCatching {
    val bitmap = Bitmap.createBitmap(900, 1350, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.rgb(8, 36, 75)
    canvas.drawRoundRect(12f, 12f, 888f, 1338f, 38f, 38f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(28f, 28f, 872f, 1322f, 30f, 30f, paint)

    paint.color = android.graphics.Color.rgb(8, 36, 75)
    canvas.drawRoundRect(28f, 28f, 872f, 250f, 30f, 30f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 48f
    canvas.drawText("TARGET TUTORIAL HOME", 450f, 115f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textSize = 25f
    canvas.drawText("OFFICIAL DIGITAL STUDENT ID", 450f, 165f, paint)

    photo?.let {
        val scaled = Bitmap.createScaledBitmap(it, 250, 250, true)
        canvas.drawBitmap(scaled, 325f, 285f, paint)
    }
    paint.color = android.graphics.Color.rgb(8, 36, 75)
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 45f
    canvas.drawText(name.ifBlank { "Student Name" }.take(25), 450f, 610f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textSize = 25f
    canvas.drawText("Student ID: ${id.ifBlank { "Not verified" }}".take(42), 450f, 660f, paint)

    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 26f
    paint.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText("STUDENT INFORMATION", 90f, 740f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textSize = 23f
    canvas.drawText("Phone: ${phone.ifBlank { "Not provided" }}".take(55), 90f, 795f, paint)
    canvas.drawText("Address: ${address.ifBlank { "Not provided" }}".take(58), 90f, 845f, paint)
    canvas.drawText("Class: ${className.ifBlank { "Class not set" }}".take(55), 90f, 895f, paint)
    canvas.drawText("Batch: ${batchName.ifBlank { "Batch not assigned" }}".take(55), 90f, 945f, paint)

    qr?.let {
        canvas.drawBitmap(Bitmap.createScaledBitmap(it, 160, 160, true), 370f, 970f, paint)
    }
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 17f
    canvas.drawText("SCAN TO VERIFY STUDENT ID", 450f, 1150f, paint)
    canvas.drawBitmap(Bitmap.createScaledBitmap(signature, 230, 74, true), 335f, 1160f, paint)
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 20f
    canvas.drawText("TEACHER: NIBEDAN SARKAR", 450f, 1275f, paint)
    canvas.drawText("N. SARKAR  |  AUTHORIZED SIGNATORY", 450f, 1305f, paint)

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "TargetTutorial_ID_${safeName(id)}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/TargetTutorial"
            )
        }
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ) ?: error("Gallery-তে Image তৈরি করা যায়নি")
    context.contentResolver.openOutputStream(uri)?.use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    } ?: error("Image লেখা যায়নি")
    "Pictures/TargetTutorial"
}

private fun safeName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "student" }

private fun studentInitials(name: String): String =
    name.trim().split(Regex("\\s+"))
        .take(2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .ifBlank { "ST" }
