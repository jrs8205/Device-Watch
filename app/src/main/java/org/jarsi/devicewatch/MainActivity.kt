package org.jarsi.devicewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.devicewatch.presentation.DashboardViewModel
import org.jarsi.devicewatch.presentation.ui.SystemDashboardScreen
import org.jarsi.devicewatch.ui.theme.ModernWidgetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The same activity-scoped ViewModel the dashboard uses, so flipping the
            // look switch in Settings re-themes the whole tree at once.
            val viewModel: DashboardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ModernWidgetTheme(classicLook = uiState.classicLook) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SystemDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
