package com.targettutorial.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

data class StudentNotice(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val deepLink: String,
    val createdAt: String
)

/**
 * Firestore-backed in-app notification inbox (not remote push).
 *
 * Collection notifications:
 * title, message, type, targetType(all|class|batch|student), targetValue,
 * active, deepLink, createdAt
 *
 * Read state: notificationReads/{uid_notificationId}
 */
@Composable
fun NotificationScreen(
    studentUid: String,
    studentId: String,
    studentClass: String = "",
    batch: String = "",
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    onOpenLink: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var notices by remember { mutableStateOf<List<StudentNotice>>(emptyList()) }
    var readIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(studentUid, studentId, studentClass, batch) {
        var noticeListener: ListenerRegistration? = null
        var readListener: ListenerRegistration? = null

        noticeListener = db.collection("notifications")
            .whereEqualTo("active", true)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.localizedMessage ?: "Notification load failed"; loading = false; return@addSnapshotListener }
                notices = snap?.documents.orEmpty().mapNotNull { doc ->
                    val targetType = doc.getString("targetType") ?: "all"
                    val targetValue = doc.getString("targetValue").orEmpty()
                    val matches = when (targetType) {
                        "all" -> true
                        "class" -> targetValue == studentClass
                        "batch" -> targetValue == batch
                        "student" -> targetValue == studentId || targetValue == studentUid
                        else -> false
                    }
                    if (!matches) return@mapNotNull null
                    val date = doc.getTimestamp("createdAt")?.toDate()?.let {
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
                    } ?: ""
                    StudentNotice(
                        doc.id, doc.getString("title") ?: "Notification",
                        doc.getString("message") ?: "", doc.getString("type") ?: "general",
                        doc.getString("deepLink") ?: "", date
                    )
                }.sortedByDescending { it.createdAt }
                loading = false
            }

        if (studentUid.isNotBlank()) {
            readListener = db.collection("notificationReads")
                .whereEqualTo("uid", studentUid)
                .addSnapshotListener { snap, _ ->
                    readIds = snap?.documents?.mapNotNull { it.getString("notificationId") }?.toSet().orEmpty()
                }
        }
        onDispose { noticeListener?.remove(); readListener?.remove() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Notifications", style = MaterialTheme.typography.headlineSmall)
        Text("${notices.count { it.id !in readIds }} unread", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator()
            error.isNotBlank() -> Text(error, color = MaterialTheme.colorScheme.error)
            notices.isEmpty() -> Text("নতুন কোনো Notification নেই।")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(notices, key = { it.id }) { item ->
                    val unread = item.id !in readIds
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = if (unread) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                    ), onClick = {
                        if (studentUid.isNotBlank()) {
                            db.collection("notificationReads").document("${studentUid}_${item.id}")
                                .set(mapOf("uid" to studentUid, "notificationId" to item.id, "readAt" to FieldValue.serverTimestamp()))
                        }
                        if (item.deepLink.isNotBlank()) onOpenLink(item.deepLink)
                    }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.message, style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.type} • ${item.createdAt}", style = MaterialTheme.typography.labelSmall)
                                if (unread) Text("NEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
