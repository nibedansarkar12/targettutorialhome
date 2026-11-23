package com.targettutorial.home

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Stores preferences locally on this phone.
 * Connect onThemeChanged/onLanguageChanged to the app-wide theme and localization later.
 */
@Composable
fun StudentSettingsScreen(
    onBack: () -> Unit = {},
    onThemeChanged: (Boolean) -> Unit = {},
    onLanguageChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("target_tutorial_settings", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var language by remember { mutableStateOf(prefs.getString("language", "বাংলা") ?: "বাংলা") }
    var noticeEnabled by remember { mutableStateOf(prefs.getBoolean("notice_enabled", true)) }
    var examEnabled by remember { mutableStateOf(prefs.getBoolean("exam_enabled", true)) }
    var materialEnabled by remember { mutableStateOf(prefs.getBoolean("material_enabled", true)) }
    var achievementEnabled by remember { mutableStateOf(prefs.getBoolean("achievement_enabled", true)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Profile & Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        SettingSwitch("Dark Mode", darkMode) {
            darkMode = it
            prefs.edit().putBoolean("dark_mode", it).apply()
            onThemeChanged(it)
        }
        Spacer(Modifier.height(12.dp))
        Text("Language", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = language == "বাংলা", onClick = {
                language = "বাংলা"; prefs.edit().putString("language", language).apply(); onLanguageChanged(language)
            }, label = { Text("বাংলা") })
            FilterChip(selected = language == "English", onClick = {
                language = "English"; prefs.edit().putString("language", language).apply(); onLanguageChanged(language)
            }, label = { Text("English") })
        }
        Spacer(Modifier.height(18.dp))
        Text("Notification Preferences", style = MaterialTheme.typography.titleMedium)
        SettingSwitch("Notice notifications", noticeEnabled) {
            noticeEnabled = it; prefs.edit().putBoolean("notice_enabled", it).apply()
        }
        SettingSwitch("Exam reminders", examEnabled) {
            examEnabled = it; prefs.edit().putBoolean("exam_enabled", it).apply()
        }
        SettingSwitch("New study materials", materialEnabled) {
            materialEnabled = it; prefs.edit().putBoolean("material_enabled", it).apply()
        }
        SettingSwitch("Achievements", achievementEnabled) {
            achievementEnabled = it; prefs.edit().putBoolean("achievement_enabled", it).apply()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "এই পছন্দগুলো এই ফোনে সেভ হয়। Dark Mode/Language পুরো অ্যাপে কার্যকর করতে MainActivity-এর Theme ও Text resources-এর সঙ্গে সংযোগ করতে হবে।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
