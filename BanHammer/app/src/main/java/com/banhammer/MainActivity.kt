package com.banhammer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.banhammer.ui.BanHammerApp
import com.banhammer.ui.BanHammerTheme

class MainActivity : ComponentActivity() {
    private lateinit var state: BanState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = BanState(applicationContext)
        state.tick()
        setContent { BanHammerTheme { BanHammerApp(state) } }
    }

    override fun onResume() {
        super.onResume()
        state.tick()
        state.loadApps() // picks up apps installed or removed since last time
    }
}
