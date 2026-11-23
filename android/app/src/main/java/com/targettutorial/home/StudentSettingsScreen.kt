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

@Composable
fun StudentSettingsScreen(
    darkModeValue: Boolean = false,
    languageValue: String = "বাংলা",
    onBack: () -> Unit = {},
    onThemeChanged: (Boolean) -> Unit = {},
    onLanguageChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("target_tutorial_settings", Context.MODE_PRIVATE)
    }

    var darkMode by remember(darkModeValue) { mutableStateOf(darkModeValue) }
    var language by remember(languageValue) { mutableStateOf(languageValue) }
    var noticeEnabled by remember { mutableStateOf(prefs.getBoolean("notice_enabled", true)) }
    var examEnabled by remember { mutableStateOf(prefs.getBoolean("exam_enabled", true)) }
    var materialEnabled by remember { mutableStateOf(prefs.getBoolean("material_enabled", true)) }
    var achievementEnabled by remember { mutableStateOf(prefs.getBoolean("achievement_enabled", true)) }

    val english = language == "English"

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(if (english) "← Back" else "← ফিরে যাও")
        }
        Text(
            if (english) "Profile & Settings" else "প্রোফাইল ও সেটিংস",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(20.dp))

        Text(if (english) "Appearance" else "চেহারা", style = MaterialTheme.typography.titleMedium)
        SettingSwitch(if (english) "Dark Mode" else "ডার্ক মোড", darkMode) {
            darkMode = it
            prefs.edit().putBoolean("dark_mode", it).apply()
            onThemeChanged(it)
        }

        Spacer(Modifier.height(18.dp))
        Text(if (english) "Language" else "ভাষা", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = language == "বাংলা",
                onClick = {
                    language = "বাংলা"
                    prefs.edit().putString("language", language).apply()
                    onLanguageChanged(language)
                },
                label = { Text("বাংলা") }
            )
            FilterChip(
                selected = language == "English",
                onClick = {
                    language = "English"
                    prefs.edit().putString("language", language).apply()
                    onLanguageChanged(language)
                },
                label = { Text("English") }
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            if (english) "Notification Preferences" else "নোটিফিকেশন পছন্দ",
            style = MaterialTheme.typography.titleMedium
        )
        SettingSwitch(if (english) "Notice notifications" else "নোটিশ নোটিফিকেশন", noticeEnabled) {
            noticeEnabled = it
            prefs.edit().putBoolean("notice_enabled", it).apply()
        }
        SettingSwitch(if (english) "Exam reminders" else "পরীক্ষার রিমাইন্ডার", examEnabled) {
            examEnabled = it
            prefs.edit().putBoolean("exam_enabled", it).apply()
        }
        SettingSwitch(if (english) "New study materials" else "নতুন পড়ার সামগ্রী", materialEnabled) {
            materialEnabled = it
            prefs.edit().putBoolean("material_enabled", it).apply()
        }
        SettingSwitch(if (english) "Achievements" else "অর্জন ও পুরস্কার", achievementEnabled) {
            achievementEnabled = it
            prefs.edit().putBoolean("achievement_enabled", it).apply()
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (english) {
                "Preferences are saved on this phone. Dark Mode is connected to the app theme. Language is connected to this screen and the main dashboard; other screens still need translation."
            } else {
                "পছন্দগুলো এই ফোনে সেভ হয়। Dark Mode অ্যাপের Theme-এর সঙ্গে যুক্ত। Language এই স্ক্রিন ও মূল Dashboard-এ কাজ করে; অন্য স্ক্রিনগুলোর লেখা আলাদাভাবে অনুবাদ করতে হবে।"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
