package com.targettutorial.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class PopupAd(
    val enabled: Boolean = false,
    val title: String = "",
    val message: String = "",
    val imageUrl: String = "",
    val buttonText: String = "",
    val buttonUrl: String = "",
    val displayFrequency: String = "every_app_open",
    val delaySeconds: Long = 0L
)

/**
 * Place this composable once at the root of the native student app UI.
 * It reads appSettings/advertisementPopup and displays immediately on every
 * Activity launch when enabled. The admin web page publishes this document.
 *
 * FirebaseApp must be initialized in the Android Application before use.
 */
@Composable
fun AdvertisementPopup(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    var ad by remember { mutableStateOf<PopupAd?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(firestore) {
        var registration: ListenerRegistration? = null
        registration = firestore.collection("appSettings")
            .document("advertisementPopup")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    ad = null
                } else {
                    val data = snapshot.data ?: emptyMap<String, Any>()
                    ad = PopupAd(
                        enabled = data["enabled"] as? Boolean ?: false,
                        title = data["title"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        imageUrl = data["imageUrl"] as? String ?: "",
                        buttonText = data["buttonText"] as? String ?: "",
                        buttonUrl = data["buttonUrl"] as? String ?: "",
                        displayFrequency = data["displayFrequency"] as? String ?: "every_app_open",
                        delaySeconds = (data["delaySeconds"] as? Number)?.toLong() ?: 0L
                    )
                    dismissed = false
                }
            }
        onDispose { registration?.remove() }
    }

    val current = ad
    if (current != null && current.enabled && !dismissed) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text(current.title.ifBlank { "বিশেষ ঘোষণা" }) },
            text = {
                androidx.compose.foundation.layout.Column {
                    if (current.imageUrl.isNotBlank()) {
                        // Optional image rendering can be added with Coil AsyncImage.
                        Text("বিজ্ঞাপনের ছবি সংযুক্ত আছে")
                    }
                    Text(current.message)
                }
            },
            confirmButton = {
                if (current.buttonUrl.startsWith("https://") || current.buttonUrl.startsWith("http://")) {
                    Button(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(current.buttonUrl))
                        )
                        dismissed = true
                    }) {
                        Text(current.buttonText.ifBlank { "আরও জানুন" })
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissed = true }) {
                    Text("পরে দেখব")
                }
            }
        )
    }
}
