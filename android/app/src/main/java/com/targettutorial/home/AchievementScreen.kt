package com.targettutorial.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class StudentAchievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val earned: Boolean
)

/**
 * Reads achievement definitions and this student's awards from Firestore.
 *
 * Suggested collections:
 * achievements/{achievementId}: title, description, icon, active
 * studentAchievements/{recordId}: uid, achievementId, earnedAt
 *
 * Only trusted Admin/server logic should award badges. Never let a student write
 * their own studentAchievements records.
 */
@Composable
fun AchievementScreen(
    studentUid: String,
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    onBack: () -> Unit = {}
) {
    var rows by remember { mutableStateOf<List<StudentAchievement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(studentUid) {
        var definitions: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
        var awards: Set<String> = emptySet()
        var defsReady = false
        var awardsReady = false
        var defsListener: ListenerRegistration? = null
        var awardsListener: ListenerRegistration? = null

        fun refresh() {
            if (!defsReady || !awardsReady) return
            rows = definitions.map { doc ->
                val id = doc.id
                StudentAchievement(
                    id = id,
                    title = doc.getString("title") ?: "Achievement",
                    description = doc.getString("description") ?: "",
                    icon = doc.getString("icon") ?: "🏅",
                    earned = id in awards
                )
            }
            loading = false
        }

        defsListener = db.collection("achievements")
            .whereEqualTo("active", true)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.localizedMessage ?: "Achievement load failed"; loading = false; return@addSnapshotListener }
                definitions = snap?.documents.orEmpty()
                defsReady = true
                refresh()
            }

        if (studentUid.isBlank()) {
            awardsReady = true
            refresh()
        } else {
            awardsListener = db.collection("studentAchievements")
                .whereEqualTo("uid", studentUid)
                .addSnapshotListener { snap, e ->
                    if (e != null) { error = e.localizedMessage ?: "Awards load failed"; loading = false; return@addSnapshotListener }
                    awards = snap?.documents?.mapNotNull { it.getString("achievementId") }?.toSet().orEmpty()
                    awardsReady = true
                    refresh()
                }
        }

        onDispose { defsListener?.remove(); awardsListener?.remove() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("My Achievements", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator()
            error.isNotBlank() -> Text(error, color = MaterialTheme.colorScheme.error)
            rows.isEmpty() -> Text("এখনো কোনো Achievement প্রকাশ করা হয়নি।")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(item.icon, style = MaterialTheme.typography.headlineMedium)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.description, style = MaterialTheme.typography.bodySmall)
                                Text(if (item.earned) "অর্জিত ✓" else "এখনো অর্জিত হয়নি", color = if (item.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
