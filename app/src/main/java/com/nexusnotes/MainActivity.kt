package com.nexusnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexusnotes.core.design.theme.NexusTheme
import com.nexusnotes.navigation.NexusNotesRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NexusTheme {
                NexusNotesRoot()
            }
        }
    }
}
