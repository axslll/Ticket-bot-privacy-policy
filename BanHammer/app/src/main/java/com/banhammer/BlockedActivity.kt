package com.banhammer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banhammer.ui.BanHammerTheme
import com.banhammer.ui.Pal

/** Full-screen "nope" shown right after a banned app/site is sent away. Closes itself. */
class BlockedActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var what by mutableStateOf("That")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        read(intent)
        setContent {
            BanHammerTheme {
                val left = Prefs.describeRemaining(this)
                Column(
                    Modifier.fillMaxSize().background(Pal.Bg).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🚫", fontSize = 84.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("$what is banned", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pal.Text, textAlign = TextAlign.Center)
                    Text(
                        if (left.isNotEmpty()) "You locked this ban. $left left." else "You banned this. Go do something else.",
                        fontSize = 16.sp, color = Pal.Dim, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                    )
                    Button(
                        onClick = { finish() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pal.Red, contentColor = Color.White),
                    ) { Text("OK, go home", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        what = intent.getStringExtra(EXTRA_WHAT) ?: "That"
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, 3500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_WHAT = "what"
    }
}
