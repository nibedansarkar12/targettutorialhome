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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File

private val IdNavy = Color(0xFF102746)
private val IdGold = Color(0xFFE3BD78)
private val IdBackground = Color(0xFFF0F4FA)

@Composable
fun StudentIdCardScreen(
    studentName: String,
    studentId: String,
    className: String = "",
    batch: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var photoBitmap by remember { mutableStateOf(loadLocalPhoto(context, studentId)) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var currentClass by remember { mutableStateOf(className) }
    var currentBatch by remember { mutableStateOf(batch) }
    var status by remember { mutableStateOf("") }

    val signatureBitmap = remember {
        android.graphics.BitmapFactory.decodeResource(
            context.resources,
            R.drawable.n_sarkar_signature
        )
    }

    // Read optional student fields from students/{studentId}.
    // Supports common field-name variants; no student photo is uploaded.
    LaunchedEffect(studentId) {
        if (studentId.isNotBlank()) {
            FirebaseFirestore.getInstance()
                .collection("students")
                .document(studentId)
                .get()
                .addOnSuccessListener { doc ->
                    phone = firstNonBlank(
                        doc.getString("phone"),
                        doc.getString("phoneNumber"),
                        doc.getString("mobile"),
                        doc.getString("mobileNumber"),
                        doc.getString("whatsapp")
                    )
                    address = firstNonBlank(
                        doc.getString("address"),
                        doc.getString("fullAddress"),
                        doc.getString("village"),
                        doc.getString("studentAddress")
                    )
                    currentClass = firstNonBlank(
                        doc.getString("class"),
                        doc.getString("className"),
                        doc.getString("studentClass"),
                        className
                    )
                    currentBatch = firstNonBlank(doc.getString("batch"), batch)
                }
                .addOnFailureListener {
                    status = "শিক্ষার্থীর অতিরিক্ত তথ্য লোড হয়নি। Firestore permission বা document ID পরীক্ষা করো।"
                }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val target = File(context.filesDir, "student_photo_${safeName(studentId)}.jpg")
                    target.outputStream().use { output -> input.copyTo(output) }
                    android.graphics.BitmapFactory.decodeFile(target.absolutePath)
                } ?: error("ছবি খোলা যায়নি")
            }.onSuccess {
                photoBitmap = it
                status = if (it != null) "ছবি এই ফোনে সেভ হয়েছে।" else "ছবি পড়া যায়নি।"
            }.onFailure {
                status = "ছবি সেভ করা যায়নি: ${it.localizedMessage}"
            }
        }
    }

    val qrBitmap = remember(studentId) {
        runCatching { createQrBitmap(studentId.ifBlank { "UNVERIFIED" }) }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Digital Student ID", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = IdNavy)
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                // Premium fixed institute header
                Column(
                    Modifier.fillMaxWidth()
                        .background(IdNavy, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TARGET TUTORIAL HOME",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "OFFICIAL DIGITAL STUDENT ID",
                        color = Color(0xFFD7E5F8),
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(IdGold))
                }

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap!!.asImageBitmap(),
                            contentDescription = "Student photo",
                            modifier = Modifier.size(100.dp)
                                .background(Color(0xFFE8EEF7), RoundedCornerShape(18.dp))
                        )
                    } else {
                        Box(
                            Modifier.size(100.dp)
                                .background(Color(0xFFE8EEF7), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                initials(studentName),
                                color = IdNavy,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            studentName.ifBlank { "Student Name" },
                            color = IdNavy,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Student ID: ${studentId.ifBlank { "Not verified" }}",
                            color = Color(0xFF596A80),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(7.dp))
                        Surface(
                            color = Color(0xFFE3F4EB),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                if (studentId.isBlank()) "NOT VERIFIED" else "VERIFIED STUDENT",
                                color = Color(0xFF187348),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "STUDENT INFORMATION",
                    color = IdNavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("Phone Number", phone.ifBlank { "Not provided" })
                InfoRow("Address", address.ifBlank { "Not provided" })
                InfoRow("Class", currentClass.ifBlank { "—" })
                InfoRow("Batch", currentBatch.ifBlank { "—" })

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFFF4F7FB), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("INSTITUTE", color = Color(0xFF77869A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("TARGET TUTORIAL HOME", color = IdNavy, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Text("TEACHER", color = Color(0xFF77869A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("Nibedan Sarkar", color = IdNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Student ID QR code",
                            modifier = Modifier.size(82.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE4EAF2))
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Image(
                            bitmap = signatureBitmap.asImageBitmap(),
                            contentDescription = "N Sarkar signature design",
                            modifier = Modifier.width(145.dp).height(48.dp)
                        )
                        Text("N. SARKAR", color = IdNavy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("AUTHORIZED SIGNATORY", color = Color(0xFF77869A), fontSize = 8.sp)
                    }
                    Text(
                        "TARGET TUTORIAL HOME",
                        color = IdNavy,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))
        OutlinedButton(
            onClick = { photoPicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (photoBitmap == null) "Add Student Photo (Optional)" else "Change Photo")
        }

        Button(
            onClick = {
                val result = saveIdCardPdf(
                    context, studentName, studentId, phone, address,
                    currentClass, currentBatch, qrBitmap, photoBitmap, signatureBitmap
                )
                status = result.fold(
                    onSuccess = { "ID Card PDF ফোনে সেভ হয়েছে: $it" },
                    onFailure = { "PDF সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = IdNavy)
        ) {
            Text("Download Premium ID Card PDF")
        }

        OutlinedButton(
            onClick = {
                val result = saveIdCardImage(
                    context, studentName, studentId, phone, address,
                    currentClass, currentBatch, qrBitmap, photoBitmap, signatureBitmap
                )
                status = result.fold(
                    onSuccess = { "ID Card Image Gallery-তে সেভ হয়েছে।" },
                    onFailure = { "Image সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save ID Card Image")
        }

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Text(
            "শিক্ষার্থীর ছবি ও তৈরি করা ID Card এই ফোনেই থাকবে; Firebase Storage-এ আপলোড হবে না।",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF77869A), fontSize = 11.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = IdNavy,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

private fun createQrBitmap(value: String): Bitmap {
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

private fun saveIdCardPdf(
    context: Context,
    name: String,
    id: String,
    phone: String,
    address: String,
    className: String,
    batch: String,
    qr: Bitmap?,
    photo: Bitmap?,
    signature: Bitmap
): Result<String> = runCatching {
    val doc = PdfDocument()
    try {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(900, 570, 1).create())
        val c = page.canvas
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = android.graphics.Color.rgb(16, 39, 70)
        c.drawRoundRect(15f, 15f, 885f, 555f, 30f, 30f, p)
        p.color = android.graphics.Color.WHITE
        c.drawRoundRect(32f, 32f, 868f, 538f, 23f, 23f, p)

        p.color = android.graphics.Color.rgb(16, 39, 70)
        c.drawRoundRect(32f, 32f, 868f, 145f, 23f, 23f, p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 34f
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("TARGET TUTORIAL HOME", 65f, 82f, p)
        p.textSize = 18f
        p.typeface = Typeface.DEFAULT
        c.drawText("OFFICIAL DIGITAL STUDENT ID", 65f, 115f, p)

        photo?.let {
            c.drawBitmap(Bitmap.createScaledBitmap(it, 145, 145, true), 65f, 175f, p)
        }
        p.color = android.graphics.Color.rgb(16, 39, 70)
        p.textSize = 29f
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(name.ifBlank { "Student Name" }.take(28), 245f, 215f, p)
        p.textSize = 17f
        p.typeface = Typeface.DEFAULT
        c.drawText("Student ID: ${id.ifBlank { "Not verified" }}".take(48), 245f, 250f, p)
        c.drawText("Phone: ${phone.ifBlank { "Not provided" }}".take(60), 245f, 292f, p)
        c.drawText("Address: ${address.ifBlank { "Not provided" }}".take(66), 245f, 325f, p)
        c.drawText("Class: ${className.ifBlank { "—" }}   Batch: ${batch.ifBlank { "—" }}".take(65), 245f, 358f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 18f
        c.drawText("Teacher: Nibedan Sarkar", 65f, 430f, p)
        c.drawText("TARGET TUTORIAL HOME", 65f, 462f, p)

        qr?.let {
            c.drawBitmap(Bitmap.createScaledBitmap(it, 105, 105, true), 720f, 385f, p)
        }
        c.drawBitmap(Bitmap.createScaledBitmap(signature, 180, 58, true), 490f, 390f, p)
        p.textSize = 13f
        c.drawText("N. SARKAR  |  AUTHORIZED SIGNATORY", 490f, 465f, p)

        doc.finishPage(page)

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
        ) ?: error("Documents folder-এ PDF তৈরি করা যায়নি")
        context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            ?: error("PDF লেখা যায়নি")
        filename
    } finally {
        doc.close()
    }
}

private fun saveIdCardImage(
    context: Context,
    name: String,
    id: String,
    phone: String,
    address: String,
    className: String,
    batch: String,
    qr: Bitmap?,
    photo: Bitmap?,
    signature: Bitmap
): Result<String> = runCatching {
    val bitmap = Bitmap.createBitmap(1200, 760, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bitmap)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    p.color = android.graphics.Color.rgb(16, 39, 70)
    c.drawRoundRect(12f, 12f, 1188f, 748f, 42f, 42f, p)
    p.color = android.graphics.Color.WHITE
    c.drawRoundRect(30f, 30f, 1170f, 730f, 32f, 32f, p)

    p.color = android.graphics.Color.rgb(16, 39, 70)
    c.drawRoundRect(30f, 30f, 1170f, 180f, 32f, 32f, p)
    p.color = android.graphics.Color.WHITE
    p.textSize = 52f
    p.typeface = Typeface.DEFAULT_BOLD
    c.drawText("TARGET TUTORIAL HOME", 75f, 100f, p)
    p.textSize = 25f
    p.typeface = Typeface.DEFAULT
    c.drawText("OFFICIAL DIGITAL STUDENT ID", 75f, 145f, p)

    photo?.let {
        c.drawBitmap(Bitmap.createScaledBitmap(it, 220, 220, true), 75f, 225f, p)
    }
    p.color = android.graphics.Color.rgb(16, 39, 70)
    p.textSize = 43f
    p.typeface = Typeface.DEFAULT_BOLD
    c.drawText(name.ifBlank { "Student Name" }.take(26), 350f, 290f, p)
    p.textSize = 27f
    p.typeface = Typeface.DEFAULT
    c.drawText("Student ID: ${id.ifBlank { "Not verified" }}".take(38), 350f, 345f, p)
    c.drawText("Phone: ${phone.ifBlank { "Not provided" }}".take(48), 350f, 405f, p)
    c.drawText("Address: ${address.ifBlank { "Not provided" }}".take(55), 350f, 455f, p)
    c.drawText("Class: ${className.ifBlank { "—" }}   Batch: ${batch.ifBlank { "—" }}".take(55), 350f, 505f, p)

    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = 28f
    c.drawText("Teacher: Nibedan Sarkar", 75f, 610f, p)
    p.textSize = 22f
    c.drawText("TARGET TUTORIAL HOME", 75f, 650f, p)

    qr?.let {
        c.drawBitmap(Bitmap.createScaledBitmap(it, 145, 145, true), 970f, 535f, p)
    }
    c.drawBitmap(Bitmap.createScaledBitmap(signature, 230, 74, true), 660f, 540f, p)
    p.textSize = 18f
    c.drawText("N. SARKAR  |  AUTHORIZED SIGNATORY", 660f, 635f, p)

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

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+"))
        .take(2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .ifBlank { "ST" }
