package com.targettutorial.home

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class FeeReceipt(
    val receiptNumber: String,
    val studentName: String,
    val studentId: String,
    val feeTitle: String,
    val amountRupees: Double,
    val paymentId: String,
    val orderId: String,
    val paidAtText: String
)

object PaymentReceiptPdf {
    fun create(context: Context, receipt: FeeReceipt): File {
        val folder = File(context.cacheDir, "receipts").apply { mkdirs() }
        val safeName = receipt.receiptNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(folder, "$safeName.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(15, 32, 65); textSize = 22f; isFakeBoldText = true }
        canvas.drawText("TARGET TUTORIAL HOME", 48f, 70f, paint)

        paint.textSize = 17f
        canvas.drawText("OFFICIAL PAYMENT RECEIPT", 48f, 112f, paint)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.DKGRAY

        val lines = listOf(
            "Receipt No: ${receipt.receiptNumber}",
            "Student Name: ${receipt.studentName}",
            "Student ID: ${receipt.studentId}",
            "Fee: ${receipt.feeTitle}",
            "Amount Paid: INR ${"%.2f".format(java.util.Locale.US, receipt.amountRupees)}",
            "Payment Date: ${receipt.paidAtText}",
            "UPI Transaction ID: ${receipt.paymentId}",
            "Payment Method: ${receipt.orderId}",
            "Payment Status: SUCCESSFUL / CAPTURED"
        )
        var y = 165f
        lines.forEach { line ->
            canvas.drawText(line.take(88), 48f, y, paint)
            y += 34f
        }
        paint.textSize = 10f
        canvas.drawText("Computer generated receipt. No physical signature is required.", 48f, 500f, paint)
        document.finishPage(page)

        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun shareToWhatsApp(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Target Tutorial fee payment receipt")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Target Tutorial fee payment receipt")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "রসিদ Share করো"))
        }
    }
}
