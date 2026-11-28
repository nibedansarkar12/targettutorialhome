package com.targettutorial.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FeePlanUi(val id: String, val title: String, val amountPaise: Long, val month: String, val year: String)

@Composable
fun PaymentScreen(
    studentId: String,
    userUid: String,
    userEmail: String,
    studentName: String,
    verified: Boolean,
    paymentMessage: String,
    refreshKey: Int,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var plans by remember { mutableStateOf<List<FeePlanUi>>(emptyList()) }
    var payments by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var receiptFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
    var upiId by remember { mutableStateOf("nibedansarkar02-2@okicici") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedPlan by remember { mutableStateOf<FeePlanUi?>(null) }
    var txnId by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    fun reload() {
        loading = true
        db.collection("paymentSettings").document("upi").get()
            .addOnSuccessListener { upiId = it.getString("upiId")?.trim().takeUnless { value -> value.isNullOrBlank() } ?: "nibedansarkar02-2@okicici" }
        db.collection("feePlans").whereEqualTo("active", true).get()
            .addOnSuccessListener { snap ->
                plans = snap.documents.mapNotNull { d ->
                    val amount = d.getLong("amountPaise") ?: (d.getDouble("amountPaise")?.toLong() ?: 0L)
                    if (amount < 100) null else FeePlanUi(d.id, d.getString("title") ?: "Student Fee", amount, d.getString("feeMonth") ?: "", d.getString("feeYear") ?: "")
                }
                loading = false
            }
            .addOnFailureListener { error = it.localizedMessage ?: "Fee Plan লোড হয়নি"; loading = false }
    }

    LaunchedEffect(userUid, refreshKey, verified) { if (verified) reload() else loading = false }

    DisposableEffect(userUid, verified) {
        val registration = if (verified && userUid.isNotBlank()) {
            db.collection("studentFeePayments").whereEqualTo("uid", userUid)
                .addSnapshotListener { snap, e ->
                    if (e != null) { error = e.localizedMessage ?: "Payment History লোড হয়নি"; return@addSnapshotListener }
                    if (snap != null) {
                        val docs = snap.documents
                        payments = docs.map { doc -> (doc.data ?: emptyMap()) + ("_docId" to doc.id) }
                            .sortedByDescending { (it["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L }
                        docs.forEach { doc ->
                            val item = doc.data ?: emptyMap()
                            val receiptNo = item["receiptNumber"]?.toString().orEmpty()
                            if (item["status"] == "paid" && receiptNo.isNotBlank() && !receiptFiles.containsKey(doc.id)) {
                                val paidAt = (item["paidAt"] as? com.google.firebase.Timestamp)?.toDate()
                                val dateText = paidAt?.let { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it) } ?: "—"
                                val amountPaise = (item["amountPaise"] as? Number)?.toLong() ?: 0L
                                val pdf = runCatching { PaymentReceiptPdf.create(context, FeeReceipt(
                                    receiptNumber = receiptNo, studentName = item["studentName"]?.toString() ?: studentName,
                                    studentId = studentId, feeTitle = item["feeTitle"]?.toString() ?: "Student Fee",
                                    amountRupees = amountPaise / 100.0, paymentId = item["transactionId"]?.toString().orEmpty(),
                                    orderId = "UPI", paidAtText = dateText
                                )) }.getOrNull()
                                if (pdf != null) receiptFiles = receiptFiles + (doc.id to pdf)
                            }
                        }
                    }
                }
        } else null
        onDispose { registration?.remove() }
    }

    fun launchUpi(plan: FeePlanUi) {
        if (upiId.isBlank() || !upiId.contains("@")) { error = "Admin এখনও সঠিক UPI ID সেট করেননি।"; return }
        val amount = String.format(Locale.US, "%.2f", plan.amountPaise / 100.0)
        val note = "${plan.title} - $studentId"
        val uri = Uri.Builder().scheme("upi").authority("pay")
            .appendQueryParameter("pa", upiId)
            .appendQueryParameter("pn", "Nibedan Sarkar")
            .appendQueryParameter("tn", note)
            .appendQueryParameter("am", amount)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tr", "TTH${System.currentTimeMillis()}")
            .build()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            selectedPlan = plan
            txnId = ""
        } catch (_: Exception) { error = "কোনো UPI App পাওয়া যায়নি। Google Pay / PhonePe / BHIM ইনস্টল করুন।" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← ফিরে যাও") }
            Text("💳 UPI Fee Payment", fontWeight = FontWeight.Bold)
        }
        Text("UPI দিয়ে টাকা পাঠানোর পর Transaction ID জমা দিন। Admin যাচাই করে Paid করবেন।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!verified || studentId.isBlank() || userUid.isBlank()) {
            Spacer(Modifier.height(12.dp)); Text("পেমেন্ট করতে Login ও Student Verification সম্পন্ন করুন।", color = MaterialTheme.colorScheme.error)
            Button(onClick = onVerify, modifier = Modifier.fillMaxWidth()) { Text("Student Verification") }
        }
        if (paymentMessage.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(paymentMessage) }
        if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp)); Text("চালু Fee Plans", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (!verified || studentId.isBlank() || userUid.isBlank()) Text("Verification সম্পন্ন হলে Fee Plan দেখা যাবে।")
        else if (loading) CircularProgressIndicator()
        else if (plans.isEmpty()) Text("কোনো Fee Plan চালু নেই। Admin Firebase-এর feePlans collection-এ Plan যোগ করবেন।")
        else plans.forEach { plan ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(plan.title, fontWeight = FontWeight.Bold)
                    Text("₹${"%.2f".format(Locale.US, plan.amountPaise / 100.0)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    if (plan.month.isNotBlank()) Text(listOf(plan.month, plan.year).filter { it.isNotBlank() }.joinToString(" "))
                    Button(onClick = { error = ""; launchUpi(plan) }, modifier = Modifier.fillMaxWidth()) { Text("UPI App দিয়ে Pay করুন") }
                }
            }
        }
        Spacer(Modifier.height(20.dp)); Text("Payment History & Receipts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (payments.isEmpty()) Text("এখনও কোনো পেমেন্ট রিকোয়েস্ট নেই।")
        payments.forEach { item ->
            val status = item["status"]?.toString() ?: "pending"
            val amount = (item["amountPaise"] as? Number)?.toLong() ?: 0L
            val receiptNo = item["receiptNumber"]?.toString().orEmpty()
            val docId = item["_docId"]?.toString().orEmpty()
            val created = (item["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
            val dateText = created?.let { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it) } ?: "—"
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Text(item["feeTitle"]?.toString() ?: "Student Fee", fontWeight = FontWeight.Bold)
                    Text("₹${"%.2f".format(Locale.US, amount / 100.0)} • ${if (status == "paid") "Paid" else if (status == "rejected") "Rejected" else "Verification Pending"}")
                    Text("Transaction ID: ${item["transactionId"] ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("Receipt: ${receiptNo.ifBlank { "Admin verification-এর অপেক্ষায়" }}", style = MaterialTheme.typography.bodySmall)
                    Text(dateText, style = MaterialTheme.typography.bodySmall)
                    if (status == "paid" && receiptNo.isNotBlank()) {
                        OutlinedButton(onClick = {
                            val file = receiptFiles[docId] ?: PaymentReceiptPdf.create(context, FeeReceipt(receiptNo, item["studentName"]?.toString() ?: studentName, studentId, item["feeTitle"]?.toString() ?: "Student Fee", amount / 100.0, item["transactionId"]?.toString().orEmpty(), "UPI", dateText))
                            PaymentReceiptPdf.shareToWhatsApp(context, file)
                        }) { Text("PDF Receipt • WhatsApp Share") }
                    }
                }
            }
        }
    }

    if (selectedPlan != null) {
        AlertDialog(
            onDismissRequest = { selectedPlan = null },
            title = { Text("পেমেন্টের Transaction ID দিন") },
            text = {
                Column {
                    Text("UPI App-এ পেমেন্ট সম্পন্ন করে UPI Transaction ID / UTR এখানে লিখুন। Admin টাকা যাচাই না করা পর্যন্ত Status Pending থাকবে।")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = txnId, onValueChange = { txnId = it.trim() }, label = { Text("UPI Transaction ID / UTR") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = txnId.length >= 8 && !submitting, onClick = {
                    val plan = selectedPlan ?: return@Button
                    submitting = true
                    val ref = db.collection("studentFeePayments").document()
                    val data = hashMapOf<String, Any>(
                        "uid" to userUid, "studentId" to studentId, "studentName" to studentName,
                        "studentEmail" to userEmail, "feePlanId" to plan.id, "feeTitle" to plan.title,
                        "amountPaise" to plan.amountPaise, "transactionId" to txnId,
                        "status" to "pending", "paymentMethod" to "UPI", "createdAt" to FieldValue.serverTimestamp()
                    )
                    ref.set(data).addOnSuccessListener {
                        submitting = false; selectedPlan = null; error = "পেমেন্ট রিকোয়েস্ট জমা হয়েছে। Admin যাচাই করলে Paid হবে।"
                    }.addOnFailureListener { submitting = false; error = it.localizedMessage ?: "রিকোয়েস্ট জমা হয়নি" }
                }) { Text(if (submitting) "জমা হচ্ছে…" else "Transaction ID জমা দিন") }
            },
            dismissButton = { TextButton(onClick = { selectedPlan = null }) { Text("পরে করব") } }
        )
    }
}
