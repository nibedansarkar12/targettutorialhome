package com.targettutorial.home

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
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

private const val ID_CARD_WIDTH = 1000
private const val ID_CARD_HEIGHT = 1600
private val IdScreenBackground = Color(0xFFEAF0FA)
private val IdNavyCompose = Color(0xFF08244B)

private val IdNavy = 0xFF08244B.toInt()
private val IdNavyLight = 0xFF174579.toInt()
private val IdGold = 0xFFE8B94F.toInt()
private val IdInk = 0xFF102B52.toInt()
private val IdMuted = 0xFF536987.toInt()

@Composable
fun StudentIdCardScreen(
    studentName: String,
    studentId: String,
    className: String = "",
    batch: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val firestore = remember { FirebaseFirestore.getInstance() }

    var displayName by remember(studentId, studentName) { mutableStateOf(studentName) }
    var phone by remember(studentId) { mutableStateOf("") }
    var address by remember(studentId) { mutableStateOf("") }
    var classLabel by remember(studentId, className) { mutableStateOf(className) }
    var batchLabel by remember(studentId, batch) { mutableStateOf(batch) }
    var studentPhoto by remember(studentId) { mutableStateOf(loadStudentPhoto(context, studentId)) }
    var loading by remember(studentId) { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }

    val signatureBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.n_sarkar_signature)
    }
    val qrBitmap = remember(studentId) {
        runCatching { createStudentQr(studentId.ifBlank { "UNVERIFIED" }) }.getOrNull()
    }

    // Firestore lookup: students/{studentId} -> batches/{batchId} -> name
    LaunchedEffect(studentId) {
        loading = true
        message = ""
        if (studentId.isBlank()) {
            message = "Student ID পাওয়া যায়নি।"
            loading = false
        } else {
            firestore.collection("students").document(studentId).get()
                .addOnSuccessListener { studentDoc ->
                    if (!studentDoc.exists()) {
                        message = "students/$studentId রেকর্ড পাওয়া যায়নি।"
                        loading = false
                        return@addOnSuccessListener
                    }

                    displayName = firstNonEmpty(studentDoc.getString("name"), studentName)
                    phone = firstNonEmpty(
                        studentDoc.getString("phone"),
                        studentDoc.getString("phoneNumber"),
                        studentDoc.getString("mobile"),
                        studentDoc.getString("mobileNumber"),
                        studentDoc.getString("whatsapp")
                    )
                    address = firstNonEmpty(
                        studentDoc.getString("address"),
                        studentDoc.getString("fullAddress"),
                        studentDoc.getString("studentAddress"),
                        studentDoc.getString("village")
                    )

                    classLabel = firstNonEmpty(
                        studentDoc.getString("class"),
                        studentDoc.getString("className"),
                        studentDoc.getString("studentClass"),
                        studentDoc.getString("education"),
                        className
                    )

                    val batchId = firstNonEmpty(
                        studentDoc.getString("batch"),
                        studentDoc.getString("batchId")
                    )

                    if (batchId.isBlank()) {
                        batchLabel = "Batch not assigned"
                        if (classLabel.isBlank()) classLabel = "Class not set"
                        loading = false
                    } else {
                        firestore.collection("batches").document(batchId).get()
                            .addOnSuccessListener { batchDoc ->
                                val actualBatchName = firstNonEmpty(
                                    batchDoc.getString("name"),
                                    batchDoc.getString("batchName"),
                                    batchDoc.getString("title")
                                )
                                batchLabel = actualBatchName.ifBlank { "Batch name unavailable" }

                                if (classLabel.isBlank()) {
                                    val classNumber = Regex("(?i)\\bclass\\s*[-:]?\\s*(\\d{1,2})\\b")
                                        .find(actualBatchName)?.groupValues?.getOrNull(1)
                                    classLabel = classNumber?.let { "Class $it" } ?: "Class not set"
                                }
                                loading = false
                            }
                            .addOnFailureListener { error ->
                                batchLabel = "Batch name unavailable"
                                if (classLabel.isBlank()) classLabel = "Class not set"
                                message = "Batch তথ্য পড়া যায়নি: ${error.localizedMessage ?: "Permission denied"}"
                                loading = false
                            }
                    }
                }
                .addOnFailureListener { error ->
                    message = "Student তথ্য পড়া যায়নি: ${error.localizedMessage ?: "Permission denied"}"
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
                    val photoFile = File(context.filesDir, "student_photo_${safeStudentFileName(studentId)}.jpg")
                    photoFile.outputStream().use { output -> input.copyTo(output) }
                    BitmapFactory.decodeFile(photoFile.absolutePath)
                } ?: error("ছবি খোলা যায়নি")
            }.onSuccess { bitmap ->
                studentPhoto = bitmap
                message = if (bitmap != null) "ছবি এই ফোনে সেভ হয়েছে।" else "ছবি পড়া যায়নি।"
            }.onFailure { error ->
                message = "ছবি সেভ হয়নি: ${error.localizedMessage}"
            }
        }
    }

    // One bitmap powers preview, PDF and PNG, so all three exports have the same layout.
    val finalCardBitmap = remember(
        displayName, studentId, phone, address, classLabel, batchLabel, studentPhoto, qrBitmap, signatureBitmap
    ) {
        drawPremiumPortraitIdCard(
            displayName, studentId, phone, address, classLabel, batchLabel,
            studentPhoto, qrBitmap, signatureBitmap
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF2464B5)) }
            Text("Digital Student ID", color = IdNavyCompose, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Image(
                bitmap = finalCardBitmap.asImageBitmap(),
                contentDescription = "Target Tutorial Home portrait student ID card",
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                contentScale = ContentScale.FillWidth
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { photoPicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (studentPhoto == null) "Add Student Photo (Optional)" else "Change Student Photo")
        }
        Button(
            onClick = {
                val result = saveStudentIdPdf(context, finalCardBitmap, studentId)
                message = result.fold(
                    { "PDF সেভ হয়েছে: $it" },
                    { "PDF সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = IdNavyCompose)
        ) { Text("Download Premium ID Card PDF") }
        OutlinedButton(
            onClick = {
                val result = saveStudentIdPng(context, finalCardBitmap, studentId)
                message = result.fold(
                    { "ID Card Image Gallery-তে সেভ হয়েছে।" },
                    { "Image সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save ID Card Image") }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("Student ও Batch তথ্য লোড হচ্ছে…", fontSize = 12.sp, color = Color(0xFF34577E))
        }
        if (message.isNotBlank()) {
            Text(message, fontSize = 11.sp, color = Color(0xFF9B3B35))
        }
        Text(
            "শিক্ষার্থীর ছবি ও ID Card শুধু এই ফোনে থাকবে; Firebase Storage-এ আপলোড হবে না।",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

private fun drawPremiumPortraitIdCard(
    studentName: String,
    studentId: String,
    phone: String,
    address: String,
    className: String,
    batchName: String,
    studentPhoto: Bitmap?,
    qr: Bitmap?,
    signature: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(ID_CARD_WIDTH, ID_CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Outer gold rim and clean white card surface.
    paint.color = IdGold
    canvas.drawRoundRect(RectF(8f, 8f, 992f, 1592f), 52f, 52f, paint)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawRoundRect(RectF(20f, 20f, 980f, 1580f), 44f, 44f, paint)

    // Navy header with a smooth gradient.
    paint.shader = LinearGradient(
        20f, 20f, 980f, 390f,
        intArrayOf(0xFF061B38.toInt(), 0xFF123F78.toInt(), 0xFF071D3B.toInt()),
        null, Shader.TileMode.CLAMP
    )
    canvas.drawRoundRect(RectF(20f, 20f, 980f, 390f), 44f, 44f, paint)
    paint.shader = null

    // Compact TTH crest. The selected student photo remains the central portrait below.
    paint.color = IdGold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 9f
    canvas.drawCircle(150f, 145f, 91f, paint)
    paint.style = Paint.Style.FILL
    paint.color = 0xFFFFFFFF.toInt()
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create("serif", Typeface.BOLD)
    paint.textSize = 58f
    canvas.drawText("TTH", 150f, 166f, paint)
    paint.color = IdGold
    paint.textSize = 21f
    canvas.drawText("✦  ✦  ✦", 150f, 210f, paint)

    // Fixed institute name stays on one line; shrink-to-fit prevents clipping.
    paint.textAlign = Paint.Align.LEFT
    paint.color = 0xFFFFFFFF.toInt()
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = fitTextSize("TARGET TUTORIAL HOME", 650f, 48f, paint)
    canvas.drawText("TARGET TUTORIAL HOME", 275f, 135f, paint)
    paint.color = 0xFFDDEAFF.toInt()
    paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    paint.textSize = 24f
    canvas.drawText("OFFICIAL STUDENT IDENTITY CARD", 275f, 205f, paint)
    paint.color = IdGold
    canvas.drawRoundRect(RectF(275f, 238f, 920f, 247f), 5f, 5f, paint)

    // Controlled gold wave and pale background wave.
    val goldWave = Path().apply {
        moveTo(20f, 355f)
        cubicTo(210f, 250f, 360f, 292f, 510f, 340f)
        cubicTo(700f, 390f, 830f, 320f, 980f, 230f)
        lineTo(980f, 325f)
        cubicTo(810f, 445f, 640f, 455f, 470f, 405f)
        cubicTo(280f, 345f, 150f, 360f, 20f, 485f)
        close()
    }
    paint.color = IdGold
    canvas.drawPath(goldWave, paint)
    val paleWave = Path().apply {
        moveTo(20f, 430f)
        cubicTo(220f, 315f, 360f, 380f, 520f, 430f)
        cubicTo(710f, 485f, 850f, 400f, 980f, 330f)
        lineTo(980f, 1210f)
        cubicTo(750f, 1270f, 540f, 1220f, 350f, 1260f)
        cubicTo(200f, 1300f, 90f, 1270f, 20f, 1225f)
        close()
    }
    paint.shader = LinearGradient(
        0f, 400f, 0f, 1280f,
        intArrayOf(0xFFF8FBFF.toInt(), 0xFFE8F0FC.toInt(), 0xFFFFFFFF.toInt()),
        null, Shader.TileMode.CLAMP
    )
    canvas.drawPath(paleWave, paint)
    paint.shader = null

    // Student image: circular portrait crop. Any selected local image (including test logo) is retained.
    paint.color = IdGold
    canvas.drawCircle(500f, 465f, 158f, paint)
    paint.color = 0xFFF4F7FF.toInt()
    canvas.drawCircle(500f, 465f, 146f, paint)
    if (studentPhoto != null) {
        val save = canvas.save()
        val clip = Path().apply { addCircle(500f, 465f, 136f, Path.Direction.CW) }
        canvas.clipPath(clip)
        val source = centerCropSource(studentPhoto.width, studentPhoto.height, 272, 272)
        canvas.drawBitmap(studentPhoto, source, RectF(364f, 329f, 636f, 601f), paint)
        canvas.restoreToCount(save)
    } else {
        paint.color = IdNavyLight
        canvas.drawCircle(500f, 465f, 136f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 88f
        canvas.drawText(studentInitials(studentName), 500f, 496f, paint)
    }

    // Student name plate and ID strip.
    paint.shader = LinearGradient(
        145f, 620f, 855f, 715f,
        intArrayOf(0xFF071E3D.toInt(), 0xFF17477F.toInt(), 0xFF071E3D.toInt()),
        null, Shader.TileMode.CLAMP
    )
    canvas.drawRoundRect(RectF(145f, 625f, 855f, 715f), 46f, 46f, paint)
    paint.shader = null
    paint.color = IdGold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    canvas.drawRoundRect(RectF(145f, 625f, 855f, 715f), 46f, 46f, paint)
    paint.style = Paint.Style.FILL
    paint.color = 0xFFFFFFFF.toInt()
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val safeName = studentName.ifBlank { "Student Name" }
    paint.textSize = fitTextSize(safeName, 650f, 52f, paint)
    canvas.drawText(safeName, 500f, 684f, paint)

    paint.color = 0xFFDCE8FB.toInt()
    canvas.drawRoundRect(RectF(155f, 728f, 845f, 785f), 28f, 28f, paint)
    paint.color = IdInk
    paint.textSize = fitTextSize("Student ID: ${studentId.ifBlank { "Not verified" }}", 650f, 27f, paint)
    canvas.drawText("Student ID: ${studentId.ifBlank { "Not verified" }}", 500f, 765f, paint)

    // Information panel.
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawRoundRect(RectF(75f, 835f, 925f, 1185f), 36f, 36f, paint)
    paint.color = IdGold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    canvas.drawRoundRect(RectF(75f, 835f, 925f, 1185f), 36f, 36f, paint)
    paint.style = Paint.Style.FILL
    paint.shader = LinearGradient(
        250f, 815f, 750f, 895f,
        intArrayOf(0xFFFFD978.toInt(), 0xFFE6B344.toInt()), null, Shader.TileMode.CLAMP
    )
    canvas.drawRoundRect(RectF(255f, 807f, 745f, 882f), 36f, 36f, paint)
    paint.shader = null
    paint.color = IdNavy
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 33f
    canvas.drawText("STUDENT INFORMATION", 500f, 856f, paint)

    drawInformationRow(canvas, paint, "P", "Phone", phone.ifBlank { "Not provided" }, 930f, 26f, 420f)
    drawInformationRow(canvas, paint, "A", "Address", address.ifBlank { "Not provided" }, 1005f, 23f, 420f, maxLines = 2)
    drawInformationRow(canvas, paint, "C", "Class", className.ifBlank { "Class not set" }, 1090f, 27f, 420f)
    drawInformationRow(canvas, paint, "B", "Batch", batchName.ifBlank { "Batch not assigned" }, 1150f, 27f, 420f, drawDivider = false)

    // QR: quiet white margin, single rounded frame, balanced side marks and clear caption.
    if (qr != null) {
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(390f, 1200f, 610f, 1410f), 28f, 28f, paint)
        paint.color = IdGold
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawRoundRect(RectF(390f, 1200f, 610f, 1410f), 28f, 28f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(
            Bitmap.createScaledBitmap(qr, 176, 176, false),
            null,
            RectF(412f, 1217f, 588f, 1393f),
            paint
        )
        drawStandardQrAccents(canvas, paint)
        paint.color = IdNavy
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 18f
        canvas.drawText("SCAN TO VERIFY STUDENT ID", 500f, 1438f, paint)
    }

    // Footer wave, teacher name and high-contrast gold N Sarkar signature.
    val footerPath = Path().apply {
        moveTo(20f, 1480f)
        cubicTo(190f, 1400f, 300f, 1510f, 490f, 1490f)
        cubicTo(690f, 1470f, 815f, 1390f, 980f, 1455f)
        lineTo(980f, 1580f)
        lineTo(20f, 1580f)
        close()
    }
    paint.shader = LinearGradient(
        0f, 1430f, 0f, 1580f,
        intArrayOf(0xFF16477F.toInt(), 0xFF061B38.toInt()), null, Shader.TileMode.CLAMP
    )
    canvas.drawPath(footerPath, paint)
    paint.shader = null
    paint.color = IdGold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 7f
    canvas.drawPath(footerPath, paint)
    paint.style = Paint.Style.FILL

    paint.color = 0xFFFFFFFF.toInt()
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = fitTextSize("TEACHER: NIBEDAN SARKAR", 540f, 29f, paint)
    canvas.drawText("TEACHER: NIBEDAN SARKAR", 65f, 1525f, paint)

    if (signature != null) {
        val signatureScaled = Bitmap.createScaledBitmap(signature, 285, 92, true)
        canvas.drawBitmap(signatureScaled, 640f, 1468f, paint)
    } else {
        paint.color = IdGold
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("cursive", Typeface.NORMAL)
        paint.textSize = 52f
        canvas.drawText("N Sarkar", 785f, 1525f, paint)
    }
    paint.color = IdGold
    canvas.drawRoundRect(RectF(640f, 1560f, 930f, 1567f), 4f, 4f, paint)

    return bitmap
}

private fun drawInformationRow(
    canvas: AndroidCanvas,
    paint: Paint,
    iconLetter: String,
    label: String,
    value: String,
    baselineY: Float,
    valueTextSize: Float,
    maxValueWidth: Float,
    maxLines: Int = 1,
    drawDivider: Boolean = true
) {
    // Matching navy icon tile with a clear gold initial instead of inconsistent glyphs.
    paint.color = IdNavy
    canvas.drawRoundRect(RectF(105f, baselineY - 34f, 185f, baselineY + 34f), 18f, 18f, paint)
    paint.color = IdGold
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 30f
    canvas.drawText(iconLetter, 145f, baselineY + 10f, paint)

    paint.color = IdInk
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 29f
    canvas.drawText(label, 215f, baselineY + 10f, paint)
    paint.textSize = 28f
    canvas.drawText(":", 420f, baselineY + 10f, paint)

    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = valueTextSize
    val lines = wrapCardText(value, paint, maxValueWidth).take(maxLines)
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, 475f, baselineY + 10f + index * (valueTextSize + 4f), paint)
    }
    if (drawDivider) {
        paint.color = 0xFFD8E2F0.toInt()
        paint.strokeWidth = 2f
        canvas.drawLine(105f, baselineY + 47f, 895f, baselineY + 47f, paint)
    }
}

private fun drawStandardQrAccents(canvas: AndroidCanvas, paint: Paint) {
    paint.color = IdGold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 7f
    paint.strokeCap = Paint.Cap.ROUND
    // Matching short vertical side lines with small horizontal end caps: no leaf-like decoration.
    val top = 1240f
    val bottom = 1370f
    val left = 355f
    val right = 645f
    val cap = 18f
    canvas.drawLine(left, top, left, bottom, paint)
    canvas.drawLine(left, top, left + cap, top, paint)
    canvas.drawLine(left, bottom, left + cap, bottom, paint)
    canvas.drawLine(right, top, right, bottom, paint)
    canvas.drawLine(right - cap, top, right, top, paint)
    canvas.drawLine(right - cap, bottom, right, bottom, paint)
    paint.style = Paint.Style.FILL
    paint.strokeCap = Paint.Cap.BUTT
}

private fun wrapCardText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return listOf("")
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth) {
            current = candidate
        } else {
            if (current.isNotEmpty()) lines.add(current)
            current = word
        }
    }
    if (current.isNotEmpty()) lines.add(current)
    return lines
}

private fun fitTextSize(text: String, maxWidth: Float, preferredSize: Float, paint: Paint): Float {
    var size = preferredSize
    while (size > 20f) {
        paint.textSize = size
        if (paint.measureText(text) <= maxWidth) break
        size -= 1f
    }
    return size
}

private fun centerCropSource(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Rect {
    val sourceRatio = width.toFloat() / height.toFloat()
    val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
    return if (sourceRatio > targetRatio) {
        val cropWidth = (height * targetRatio).toInt().coerceAtLeast(1)
        val left = ((width - cropWidth) / 2).coerceAtLeast(0)
        Rect(left, 0, left + cropWidth, height)
    } else {
        val cropHeight = (width / targetRatio).toInt().coerceAtLeast(1)
        val top = ((height - cropHeight) / 2).coerceAtLeast(0)
        Rect(0, top, width, top + cropHeight)
    }
}

private fun createStudentQr(value: String): Bitmap {
    val size = 420
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x, y,
                if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            )
        }
    }
    return bitmap
}

private fun loadStudentPhoto(context: Context, studentId: String): Bitmap? {
    if (studentId.isBlank()) return null
    val file = File(context.filesDir, "student_photo_${safeStudentFileName(studentId)}.jpg")
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

private fun saveStudentIdPdf(context: Context, cardBitmap: Bitmap, studentId: String): Result<String> = runCatching {
    val pdf = PdfDocument()
    try {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(ID_CARD_WIDTH, ID_CARD_HEIGHT, 1).create())
        page.canvas.drawBitmap(cardBitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        pdf.finishPage(page)

        val fileName = "TargetTutorial_Premium_ID_${safeStudentFileName(studentId)}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/TargetTutorial")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: error("PDF ফাইল তৈরি করা যায়নি")
        context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
            ?: error("PDF লেখা যায়নি")
        fileName
    } finally {
        pdf.close()
    }
}

private fun saveStudentIdPng(context: Context, cardBitmap: Bitmap, studentId: String): Result<String> = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "TargetTutorial_Premium_ID_${safeStudentFileName(studentId)}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TargetTutorial")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Gallery-তে Image তৈরি করা যায়নি")
    context.contentResolver.openOutputStream(uri)?.use {
        cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    } ?: error("Image লেখা যায়নি")
    "Pictures/TargetTutorial"
}

private fun firstNonEmpty(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

private fun safeStudentFileName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "student" }

private fun studentInitials(name: String): String =
    name.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("").ifBlank { "ST" }
