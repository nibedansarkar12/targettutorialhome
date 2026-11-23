package com.targettutorial.home

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Shader
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File

private const val ID_W = 1000
private const val ID_H = 1600
private val ScreenBg = Color(0xFFEAF0FA)
private val CardNavy = 0xFF08244B.toInt()
private val CardNavy2 = 0xFF123D72.toInt()
private val CardGold = 0xFFE8B94F.toInt()
private val CardInk = 0xFF102B52.toInt()

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
    var photo by remember(studentId) { mutableStateOf(loadIdPhoto(context, studentId)) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }

    val signature = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.n_sarkar_signature)
    }
    val qr = remember(studentId) {
        runCatching { makeIdQr(studentId.ifBlank { "UNVERIFIED" }) }.getOrNull()
    }

    LaunchedEffect(studentId) {
        loading = true
        if (studentId.isBlank()) {
            message = "Student ID পাওয়া যায়নি।"
            loading = false
        } else {
            db.collection("students").document(studentId).get()
                .addOnSuccessListener { s ->
                    if (!s.exists()) {
                        message = "students/$studentId রেকর্ড পাওয়া যায়নি।"
                        loading = false
                        return@addOnSuccessListener
                    }
                    displayName = firstIdValue(s.getString("name"), studentName)
                    phone = firstIdValue(s.getString("phone"), s.getString("phoneNumber"), s.getString("mobile"), s.getString("mobileNumber"), s.getString("whatsapp"))
                    address = firstIdValue(s.getString("address"), s.getString("fullAddress"), s.getString("studentAddress"), s.getString("village"))
                    classLabel = firstIdValue(s.getString("class"), s.getString("className"), s.getString("studentClass"), s.getString("education"), className)
                    val batchId = firstIdValue(s.getString("batch"), s.getString("batchId"))
                    if (batchId.isBlank()) {
                        batchLabel = firstIdValue(batch, "Batch not assigned")
                        if (classLabel.isBlank()) classLabel = "Class not set"
                        loading = false
                    } else {
                        db.collection("batches").document(batchId).get()
                            .addOnSuccessListener { b ->
                                val realBatchName = firstIdValue(b.getString("name"), b.getString("batchName"), b.getString("title"))
                                batchLabel = realBatchName.ifBlank { "Batch name unavailable" }
                                if (classLabel.isBlank()) {
                                    val found = Regex("(?i)\\bclass\\s*[-:]?\\s*(\\d{1,2})\\b").find(realBatchName)?.groupValues?.getOrNull(1)
                                    classLabel = found?.let { "Class $it" } ?: "Class not set"
                                }
                                loading = false
                            }
                            .addOnFailureListener { e ->
                                batchLabel = "Batch name unavailable"
                                if (classLabel.isBlank()) classLabel = "Class not set"
                                message = "Batch তথ্য পড়া যায়নি: ${e.localizedMessage ?: "Permission denied"}"
                                loading = false
                            }
                    }
                }
                .addOnFailureListener { e ->
                    message = "Student তথ্য পড়া যায়নি: ${e.localizedMessage ?: "Permission denied"}"
                    loading = false
                }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val file = File(context.filesDir, "student_photo_${safeIdName(studentId)}.jpg")
                    file.outputStream().use { output -> input.copyTo(output) }
                    BitmapFactory.decodeFile(file.absolutePath)
                } ?: error("ছবি খোলা যায়নি")
            }.onSuccess {
                photo = it
                message = "ছবি এই ফোনে সেভ হয়েছে।"
            }.onFailure { message = "ছবি সেভ হয়নি: ${it.localizedMessage}" }
        }
    }

    val cardBitmap = remember(displayName, studentId, phone, address, classLabel, batchLabel, photo, qr, signature) {
        renderPremiumIdCard(displayName, studentId, phone, address, classLabel, batchLabel, photo, qr, signature)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ScreenBg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF2464B5)) }
            Text("Digital Student ID", color = Color(0xFF102B52), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Image(
                bitmap = cardBitmap.asImageBitmap(),
                contentDescription = "Premium vertical Target Tutorial Home student ID card",
                modifier = Modifier.fillMaxWidth().padding(7.dp),
                contentScale = ContentScale.FillWidth
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (photo == null) "Add Student Photo (Optional)" else "Change Student Photo")
        }
        Button(
            onClick = {
                val result = savePremiumIdPdf(context, cardBitmap, studentId)
                message = result.fold({ "Premium ID PDF সেভ হয়েছে: $it" }, { "PDF সেভ হয়নি: ${it.localizedMessage}" })
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B2B55))
        ) { Text("Download Premium ID Card PDF") }
        OutlinedButton(
            onClick = {
                val result = savePremiumIdPng(context, cardBitmap, studentId)
                message = result.fold({ "ID Card Image Gallery-তে সেভ হয়েছে।" }, { "Image সেভ হয়নি: ${it.localizedMessage}" })
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save ID Card Image") }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("Student ও Batch তথ্য লোড হচ্ছে…", fontSize = 12.sp, color = Color(0xFF34577E))
        }
        if (message.isNotBlank()) Text(message, fontSize = 11.sp, color = Color(0xFF9B3B35))
        Text(
            "ছবি ও ID Card এই ফোনে থাকবে; Firebase Storage-এ আপলোড হবে না।",
            fontSize = 10.sp, color = Color.Gray
        )
    }
}

private fun renderPremiumIdCard(
    studentName: String, studentId: String, phone: String, address: String,
    className: String, batchName: String, photo: Bitmap?, qr: Bitmap?, signature: Bitmap?
): Bitmap {
    val out = Bitmap.createBitmap(ID_W, ID_H, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Outer card, gold frame and white inner surface.
    p.color = CardGold
    c.drawRoundRect(RectF(8f, 8f, 992f, 1592f), 54f, 54f, p)
    p.color = 0xFFFFFFFF.toInt()
    c.drawRoundRect(RectF(20f, 20f, 980f, 1580f), 46f, 46f, p)

    // Navy header gradient.
    p.shader = LinearGradient(20f, 20f, 980f, 380f, intArrayOf(0xFF061B38.toInt(), 0xFF123F78.toInt(), 0xFF071D3B.toInt()), null, Shader.TileMode.CLAMP)
    c.drawRoundRect(RectF(20f, 20f, 980f, 390f), 46f, 46f, p)
    p.shader = null

    // Header crest: custom typographic TTH emblem (not a substituted third-party logo).
    p.color = CardGold
    p.style = Paint.Style.STROKE
    p.strokeWidth = 9f
    c.drawCircle(155f, 145f, 91f, p)
    p.style = Paint.Style.FILL
    p.color = 0xFFFFFFFF.toInt()
    p.textAlign = Paint.Align.CENTER
    p.typeface = Typeface.create("serif", Typeface.BOLD)
    p.textSize = 58f
    c.drawText("TTH", 155f, 166f, p)
    p.color = CardGold
    p.textSize = 22f
    c.drawText("✦  ✦  ✦", 155f, 210f, p)

    p.color = 0xFFFFFFFF.toInt()
    p.textAlign = Paint.Align.LEFT
    p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    p.textSize = 52f
    c.drawText("TARGET TUTORIAL", 285f, 125f, p)
    p.color = CardGold
    c.drawText("HOME", 285f, 188f, p)
    p.color = 0xFFDDEAFF.toInt()
    p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    p.textSize = 25f
    c.drawText("OFFICIAL STUDENT IDENTITY CARD", 285f, 245f, p)
    p.color = CardGold
    c.drawRoundRect(RectF(285f, 275f, 855f, 284f), 5f, 5f, p)

    // Gold and pale-blue sweeping bands.
    val goldWave = Path().apply {
        moveTo(20f, 355f); cubicTo(210f, 245f, 360f, 300f, 510f, 340f)
        cubicTo(700f, 390f, 830f, 320f, 980f, 230f); lineTo(980f, 325f)
        cubicTo(810f, 445f, 640f, 455f, 470f, 405f); cubicTo(280f, 345f, 150f, 360f, 20f, 485f); close()
    }
    p.color = CardGold
    c.drawPath(goldWave, p)
    val paleWave = Path().apply {
        moveTo(20f, 430f); cubicTo(220f, 315f, 360f, 380f, 520f, 430f)
        cubicTo(710f, 485f, 850f, 400f, 980f, 330f); lineTo(980f, 1190f)
        cubicTo(750f, 1260f, 540f, 1205f, 350f, 1260f); cubicTo(200f, 1300f, 90f, 1270f, 20f, 1225f); close()
    }
    p.shader = LinearGradient(0f, 400f, 0f, 1280f, intArrayOf(0xFFF8FBFF.toInt(), 0xFFE8F0FC.toInt(), 0xFFFFFFFF.toInt()), null, Shader.TileMode.CLAMP)
    c.drawPath(paleWave, p)
    p.shader = null

    // Student photo, clipped circularly with gold ring. Fallback initials if no photo.
    p.color = CardGold
    c.drawCircle(500f, 465f, 159f, p)
    p.color = 0xFFF4F7FF.toInt()
    c.drawCircle(500f, 465f, 147f, p)
    if (photo != null) {
        val save = c.save()
        val path = Path().apply { addCircle(500f, 465f, 137f, Path.Direction.CW) }
        c.clipPath(path)
        val src = centerCropRect(photo.width, photo.height, 274, 274)
        c.drawBitmap(photo, src, RectF(363f, 328f, 637f, 602f), p)
        c.restoreToCount(save)
    } else {
        p.color = CardNavy2
        c.drawCircle(500f, 465f, 137f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        p.textSize = 90f
        c.drawText(studentInitialsForCard(studentName), 500f, 495f, p)
    }

    // Name plate and ID strip.
    p.shader = LinearGradient(180f, 620f, 820f, 700f, intArrayOf(0xFF071E3D.toInt(), 0xFF17477F.toInt(), 0xFF071E3D.toInt()), null, Shader.TileMode.CLAMP)
    c.drawRoundRect(RectF(145f, 625f, 855f, 718f), 48f, 48f, p)
    p.shader = null
    p.color = CardGold
    p.style = Paint.Style.STROKE
    p.strokeWidth = 5f
    c.drawRoundRect(RectF(145f, 625f, 855f, 718f), 48f, 48f, p)
    p.style = Paint.Style.FILL
    p.color = 0xFFFFFFFF.toInt()
    p.textAlign = Paint.Align.CENTER
    p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    p.textSize = fitTextSize(studentName.ifBlank { "Student Name" }, 620f, 54f, p)
    c.drawText(studentName.ifBlank { "Student Name" }, 500f, 686f, p)
    p.color = 0xFFDCE8FB.toInt()
    c.drawRoundRect(RectF(155f, 730f, 845f, 790f), 30f, 30f, p)
    p.color = CardInk
    p.textSize = 30f
    p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    c.drawText("Student ID: ${studentId.ifBlank { "Not verified" }}", 500f, 770f, p)

    // Information panel with highlighted title capsule.
    p.color = 0xFFFFFFFF.toInt()
    c.drawRoundRect(RectF(75f, 850f, 925f, 1200f), 38f, 38f, p)
    p.color = CardGold
    p.style = Paint.Style.STROKE
    p.strokeWidth = 5f
    c.drawRoundRect(RectF(75f, 850f, 925f, 1200f), 38f, 38f, p)
    p.style = Paint.Style.FILL
    p.shader = LinearGradient(250f, 825f, 750f, 900f, intArrayOf(0xFFFFD978.toInt(), 0xFFE6B344.toInt()), null, Shader.TileMode.CLAMP)
    c.drawRoundRect(RectF(260f, 820f, 740f, 895f), 38f, 38f, p)
    p.shader = null
    p.color = CardNavy
    p.textAlign = Paint.Align.CENTER
    p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    p.textSize = 34f
    c.drawText("STUDENT INFORMATION", 500f, 870f, p)

    val left = 215f
    val valueX = 475f
    drawInfoRow(c, p, "☎", "Phone", phone.ifBlank { "Not provided" }, 930f, valueX, 31f)
    drawInfoRow(c, p, "⌖", "Address", address.ifBlank { "Not provided" }, 1000f, valueX, 27f, maxValueWidth = 420f)
    drawInfoRow(c, p, "✦", "Class", className.ifBlank { "Class not set" }, 1090f, valueX, 30f)
    drawInfoRow(c, p, "▦", "Batch", batchName.ifBlank { "Batch not assigned" }, 1155f, valueX, 30f)

    // QR verification block with gold frame and laurel-like leaves.
    if (qr != null) {
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(390f, 1220f, 610f, 1440f), 30f, 30f, p)
        p.color = CardGold
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        c.drawRoundRect(RectF(390f, 1220f, 610f, 1440f), 30f, 30f, p)
        p.style = Paint.Style.FILL
        c.drawBitmap(Bitmap.createScaledBitmap(qr, 180, 180, false), null, RectF(410f, 1240f, 590f, 1420f), p)
        drawLaurel(c, p, 355f, 1350f, -1)
        drawLaurel(c, p, 645f, 1350f, 1)
    }

    // Footer wave, fixed teacher name and signature artwork.
    val footer = Path().apply {
        moveTo(20f, 1450f); cubicTo(190f, 1370f, 300f, 1490f, 490f, 1470f)
        cubicTo(690f, 1450f, 815f, 1365f, 980f, 1430f); lineTo(980f, 1580f)
        lineTo(20f, 1580f); close()
    }
    p.shader = LinearGradient(0f, 1410f, 0f, 1580f, intArrayOf(0xFF16477F.toInt(), 0xFF061B38.toInt()), null, Shader.TileMode.CLAMP)
    c.drawPath(footer, p)
    p.shader = null
    p.color = CardGold
    p.style = Paint.Style.STROKE
    p.strokeWidth = 7f
    c.drawPath(footer, p)
    p.style = Paint.Style.FILL

    p.color = 0xFFFFFFFF.toInt()
    p.textAlign = Paint.Align.LEFT
    p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    p.textSize = 30f
    c.drawText("TEACHER: NIBEDAN SARKAR", 65f, 1515f, p)
    if (signature != null) {
        val sig = Bitmap.createScaledBitmap(signature, 290, 92, true)
        c.drawBitmap(sig, 635f, 1465f, p)
    } else {
        p.textSize = 50f
        p.typeface = Typeface.create("cursive", Typeface.NORMAL)
        c.drawText("N Sarkar", 680f, 1520f, p)
    }
    p.color = CardGold
    c.drawRect(630f, 1562f, 930f, 1568f, p)
    return out
}

private fun drawInfoRow(c: AndroidCanvas, p: Paint, symbol: String, label: String, value: String, y: Float, valueX: Float, valueSize: Float, maxValueWidth: Float = 420f) {
    p.color = CardNavy
    c.drawRoundRect(RectF(105f, y - 34f, 185f, y + 34f), 18f, 18f, p)
    p.color = 0xFFFFFFFF.toInt()
    p.textAlign = Paint.Align.CENTER
    p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    p.textSize = 38f
    c.drawText(symbol, 145f, y + 13f, p)
    p.color = CardInk
    p.textAlign = Paint.Align.LEFT
    p.textSize = 30f
    c.drawText(label, 215f, y + 11f, p)
    p.textSize = 28f
    c.drawText(":", 420f, y + 11f, p)
    p.textSize = valueSize
    p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val lines = wrapText(value, p, maxValueWidth)
    lines.take(2).forEachIndexed { index, line -> c.drawText(line, valueX, y + 10f + index * (valueSize + 5f), p) }
    p.color = 0xFFD8E2F0.toInt()
    p.strokeWidth = 2f
    c.drawLine(105f, y + 48f, 895f, y + 48f, p)
}

private fun drawLaurel(c: AndroidCanvas, p: Paint, x: Float, y: Float, direction: Int) {
    p.color = CardGold
    p.strokeWidth = 5f
    p.style = Paint.Style.STROKE
    val path = Path().apply { moveTo(x, y + 70f); cubicTo(x + 30f * direction, y + 10f, x + 10f * direction, y - 55f, x, y - 100f) }
    c.drawPath(path, p)
    p.style = Paint.Style.FILL
    for (i in 0..4) {
        val yy = y + 45f - i * 28f
        val xx = x + direction * (12f + i * 3f)
        c.drawOval(RectF(xx - 13f, yy - 6f, xx + 13f, yy + 6f), p)
    }
}

private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(Regex("\\s+"))
    val lines = mutableListOf<String>()
    var line = ""
    for (word in words) {
        val test = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(test) <= maxWidth || line.isEmpty()) line = test
        else { lines.add(line); line = word }
    }
    if (line.isNotEmpty()) lines.add(line)
    return lines
}

private fun fitTextSize(text: String, maxWidth: Float, preferred: Float, paint: Paint): Float {
    var size = preferred
    while (size > 28f) {
        paint.textSize = size
        if (paint.measureText(text) <= maxWidth) break
        size -= 2f
    }
    return size
}

private fun centerCropRect(width: Int, height: Int, targetW: Int, targetH: Int): Rect {
    val sourceRatio = width.toFloat() / height.toFloat()
    val targetRatio = targetW.toFloat() / targetH.toFloat()
    return if (sourceRatio > targetRatio) {
        val cropW = (height * targetRatio).toInt()
        val left = (width - cropW) / 2
        Rect(left, 0, left + cropW, height)
    } else {
        val cropH = (width / targetRatio).toInt()
        val top = (height - cropH) / 2
        Rect(0, top, width, top + cropH)
    }
}

private fun makeIdQr(value: String): Bitmap {
    val size = 420
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bitmap
}

private fun loadIdPhoto(context: Context, studentId: String): Bitmap? {
    if (studentId.isBlank()) return null
    val file = File(context.filesDir, "student_photo_${safeIdName(studentId)}.jpg")
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

private fun savePremiumIdPdf(context: Context, bitmap: Bitmap, studentId: String): Result<String> = runCatching {
    val pdf = PdfDocument()
    try {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(ID_W, ID_H, 1).create())
        page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        pdf.finishPage(page)
        val filename = "TargetTutorial_Premium_ID_${safeIdName(studentId)}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/TargetTutorial")
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: error("PDF তৈরি করা যায়নি")
        context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) } ?: error("PDF লেখা যায়নি")
        filename
    } finally { pdf.close() }
}

private fun savePremiumIdPng(context: Context, bitmap: Bitmap, studentId: String): Result<String> = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "TargetTutorial_Premium_ID_${safeIdName(studentId)}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TargetTutorial")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Gallery image তৈরি করা যায়নি")
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("Image লেখা যায়নি")
    "Pictures/TargetTutorial"
}

private fun firstIdValue(vararg values: String?): String = values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
private fun safeIdName(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "student" }
private fun studentInitialsForCard(name: String): String = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").ifBlank { "ST" }
