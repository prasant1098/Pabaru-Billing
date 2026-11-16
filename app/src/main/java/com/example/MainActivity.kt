package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppColorTheme
import com.example.ui.PabaruAppUI
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PabaruViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PabaruViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

            LaunchedEffect(Unit) {
                viewModel.initDatabase(this@MainActivity)
            }

            LaunchedEffect(Unit) {
                viewModel.events.collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            LaunchedEffect(isSystemDark) {
                viewModel.initThemeFromPreferences(this@MainActivity, isSystemDark)
                viewModel.updateSystemDarkTheme(isSystemDark)
            }

            val resolvedTheme = remember(uiState.themeMode, uiState.currentTheme, isSystemDark) {
                if (uiState.themeMode == "auto") {
                    if (isSystemDark) AppColorTheme.MidnightTeal else AppColorTheme.ClinicBlue
                } else {
                    uiState.currentTheme
                }
            }

            MyApplicationTheme(activeTheme = resolvedTheme) {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                    ) {
                        PabaruAppUI(viewModel = viewModel)
                        androidx.compose.material3.SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}
