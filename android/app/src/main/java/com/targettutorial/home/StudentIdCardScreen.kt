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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Student ID preview + optional local-only photo + QR.
 * Photo is copied into app-private phone storage. No Firebase Storage upload occurs.
 *
 * Required Gradle dependency:
 * implementation 'com.google.zxing:core:3.5.3'
 */
@Composable
fun StudentIdCardScreen(
    studentName: String,
    studentId: String,
    className: String = "",
    batch: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var photoBitmap by remember { mutableStateOf<Bitmap?>(loadLocalPhoto(context, studentId)) }
    var status by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val target = File(context.filesDir, "student_photo_${safeName(studentId)}.jpg")
                    target.outputStream().use { output -> input.copyTo(output) }
                    android.graphics.BitmapFactory.decodeFile(target.absolutePath)
                }
            }.onSuccess {
                photoBitmap = it
                status = if (it != null) "ছবি ফোনে সেভ হয়েছে।" else "ছবি পড়া যায়নি।"
            }.onFailure {
                status = "ছবি সেভ করা যায়নি: ${it.localizedMessage}"
            }
        }
    }

    val qrBitmap = remember(studentId) {
        runCatching { createQrBitmap(studentId.ifBlank { "UNVERIFIED" }) }.getOrNull()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF3F6FC))
            .padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Digital Student ID", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color.White).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.fillMaxWidth().background(Color(0xFF0B1F3A), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TARGET TUTORIAL HOME", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Text("STUDENT IDENTITY CARD", color = Color(0xFFB9D4FF), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = "Student photo",
                        modifier = Modifier.size(104.dp)
                    )
                } else {
                    Box(
                        Modifier.size(104.dp).background(Color(0xFFE5EEFF), RoundedCornerShape(52.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials(studentName), color = Color(0xFF0B1F3A), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(studentName.ifBlank { "Student" }, color = Color(0xFF0B1F3A), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Student ID: ${studentId.ifBlank { "Not verified" }}", color = Color.DarkGray)
                if (className.isNotBlank()) Text("Class: $className", color = Color.DarkGray)
                if (batch.isNotBlank()) Text("Batch: $batch", color = Color.DarkGray)
                Spacer(Modifier.height(14.dp))
                qrBitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "Student ID QR code", modifier = Modifier.size(116.dp))
                }
                Text("Scan to verify student ID", fontSize = 11.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (photoBitmap == null) "Add Photo (Optional)" else "Change Photo")
        }
        Button(
            onClick = {
                val result = saveIdCardPdf(context, studentName, studentId, className, batch, qrBitmap, photoBitmap)
                status = result.fold(
                    onSuccess = { "ID Card PDF ফোনে সেভ হয়েছে: $it" },
                    onFailure = { "PDF সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download ID Card PDF") }
        TextButton(
            onClick = {
                val result = saveIdCardImage(context, studentName, studentId, className, batch, qrBitmap, photoBitmap)
                status = result.fold(
                    onSuccess = { "ID Card Image ফোনে সেভ হয়েছে।" },
                    onFailure = { "Image সেভ হয়নি: ${it.localizedMessage}" }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save ID Card Image") }
        if (status.isNotBlank()) Text(status, color = Color(0xFF2563EB), fontSize = 12.sp)
        Text(
            "ছবি ও তৈরি করা ID Card এই ফোনেই থাকবে; Firebase Storage-এ আপলোড হবে না।",
            color = Color.Gray, fontSize = 11.sp
        )
    }
}

private fun createQrBitmap(value: String): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 420, 420)
    val bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.RGB_565)
    for (x in 0 until 420) for (y in 0 until 420) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bitmap
}

private fun loadLocalPhoto(context: Context, studentId: String): Bitmap? {
    if (studentId.isBlank()) return null
    val file = File(context.filesDir, "student_photo_${safeName(studentId)}.jpg")
    return if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath) else null
}

private fun saveIdCardPdf(
    context: Context, name: String, id: String, className: String, batch: String,
    qr: Bitmap?, photo: Bitmap?
): Result<String> = runCatching {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(600, 380, 1).create())
    val canvas = page.canvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.rgb(11, 31, 58)
    canvas.drawRoundRect(15f, 15f, 585f, 365f, 24f, 24f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 25f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("TARGET TUTORIAL HOME", 42f, 58f, paint)
    paint.textSize = 16f; paint.typeface = Typeface.DEFAULT
    canvas.drawText("STUDENT IDENTITY CARD", 42f, 85f, paint)
    photo?.let { canvas.drawBitmap(Bitmap.createScaledBitmap(it, 110, 110, true), 42f, 120f, paint) }
    paint.textSize = 23f; paint.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText(name.ifBlank { "Student" }, 180f, 155f, paint)
    paint.textSize = 17f; paint.typeface = Typeface.DEFAULT
    canvas.drawText("Student ID: ${id.ifBlank { "Not verified" }}", 180f, 190f, paint)
    if (className.isNotBlank()) canvas.drawText("Class: $className", 180f, 222f, paint)
    if (batch.isNotBlank()) canvas.drawText("Batch: $batch", 180f, 254f, paint)
    qr?.let { canvas.drawBitmap(Bitmap.createScaledBitmap(it, 110, 110, true), 430f, 215f, paint) }
    doc.finishPage(page)
    val filename = "TargetTutorial_ID_${safeName(id)}.pdf"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/TargetTutorial")
    }
    val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        ?: error("ফোনের Documents folder-এ ফাইল তৈরি করা যায়নি")
    context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) } ?: error("ফাইল লেখা যায়নি")
    doc.close()
    filename
}

private fun saveIdCardImage(
    context: Context, name: String, id: String, className: String, batch: String,
    qr: Bitmap?, photo: Bitmap?
): Result<String> = runCatching {
    val bitmap = Bitmap.createBitmap(1000, 620, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bitmap)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE; c.drawRect(0f, 0f, 1000f, 620f, p)
    p.color = android.graphics.Color.rgb(11, 31, 58); c.drawRoundRect(20f, 20f, 980f, 600f, 35f, 35f, p)
    p.color = android.graphics.Color.WHITE; p.textSize = 48f; p.typeface = Typeface.DEFAULT_BOLD
    c.drawText("TARGET TUTORIAL HOME", 65f, 100f, p)
    p.textSize = 30f; p.typeface = Typeface.DEFAULT
    c.drawText("STUDENT IDENTITY CARD", 65f, 150f, p)
    photo?.let { c.drawBitmap(Bitmap.createScaledBitmap(it, 210, 210, true), 65f, 205f, p) }
    p.textSize = 40f; p.typeface = Typeface.DEFAULT_BOLD
    c.drawText(name.take(25), 320f, 275f, p)
    p.textSize = 28f; p.typeface = Typeface.DEFAULT
    c.drawText("Student ID: ${id.take(28)}", 320f, 330f, p)
    if (className.isNotBlank()) c.drawText("Class: $className", 320f, 380f, p)
    if (batch.isNotBlank()) c.drawText("Batch: $batch", 320f, 430f, p)
    qr?.let { c.drawBitmap(Bitmap.createScaledBitmap(it, 150, 150, true), 770f, 405f, p) }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "TargetTutorial_ID_${safeName(id)}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TargetTutorial")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Gallery-তে ফাইল তৈরি করা যায়নি")
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        ?: error("Image লেখা যায়নি")
    "Gallery/TargetTutorial"
}

private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "student" }
private fun initials(name: String): String = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").ifBlank { "ST" }
