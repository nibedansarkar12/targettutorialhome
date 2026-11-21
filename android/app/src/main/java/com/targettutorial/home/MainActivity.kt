
package com.targettutorial.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF0B1F3A)
private val Blue = Color(0xFF2563EB)
private val Background = Color(0xFFF4F7FB)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(11, 31, 58)
        window.navigationBarColor = android.graphics.Color.rgb(11, 31, 58)

        setContent {
            TargetTutorialTheme {
                TargetTutorialHome()
            }
        }
    }
}

@Composable
fun TargetTutorialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            onPrimary = Color.White,
            secondary = Navy,
            background = Background,
            surface = Color.White
        ),
        content = content
    )
}

@Composable
fun TargetTutorialHome() {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(35.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Navy, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TT",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TARGET TUTORIAL",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = Navy,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Learn • Practice • Succeed",
                fontSize = 14.sp,
                color = Blue
            )

            Spacer(modifier = Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Navy
                ),
                elevation = CardDefaults.cardElevation(5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "স্বাগতম!",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "তোমার পড়াশোনা, অনলাইন পরীক্ষা " +
                            "এবং প্রয়োজনীয় শিক্ষাসামগ্রী " +
                            "এক জায়গায়।",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 16.sp,
                        lineHeight = 25.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "তোমার শিক্ষার সঙ্গী",
                        fontSize = 19.sp,
                        color = Navy,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    FeatureItem(
                        "📚",
                        "Study Materials",
                        "প্রয়োজনীয় নোটস ও পড়াশোনার সামগ্রী"
                    )

                    FeatureItem(
                        "📝",
                        "Online Examination",
                        "অনলাইন পরীক্ষা ও ফলাফল"
                    )

                    FeatureItem(
                        "📢",
                        "Notice Board",
                        "গুরুত্বপূর্ণ ঘোষণা ও আপডেট"
                    )

                    FeatureItem(
                        "👤",
                        "Student Profile",
                        "নিজের অ্যাকাউন্ট ও তথ্য"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "TARGET TUTORIAL",
                color = Navy,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Text(
                text = "Your learning journey starts here.",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun FeatureItem(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Color(0xFFEAF1FF),
                    RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 21.sp
            )
        }

        Spacer(modifier = Modifier.width(13.dp))

        Column {
            Text(
                text = title,
                color = Navy,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Text(
                text = description,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}
