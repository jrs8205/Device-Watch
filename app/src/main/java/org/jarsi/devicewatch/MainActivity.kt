package org.jarsi.devicewatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.devicewatch.data.AppVisibility
import org.jarsi.devicewatch.presentation.DashboardViewModel
import org.jarsi.devicewatch.presentation.ui.SystemDashboardScreen
import org.jarsi.devicewatch.ui.theme.ModernWidgetTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Tells the stats poll when this screen is in front, so its location-checked reads wait for that. */
    @Inject
    lateinit var appVisibility: AppVisibility

    /** Grows with each request to show the Overview; the dashboard reacts to every change. */
    private val openOverviewRequest = mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        appVisibility.onStart()
    }

    override fun onStop() {
        appVisibility.onStop()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch: a recreated screen already shows what the user moved to since.
        if (savedInstanceState == null) noteOverviewRequest(intent)
        setContent {
            // The same activity-scoped ViewModel the dashboard uses, so flipping the
            // look switch in Settings re-themes the whole tree at once.
            val viewModel: DashboardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val overviewRequest by openOverviewRequest
            ModernWidgetTheme(classicLook = uiState.classicLook) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SystemDashboardScreen(viewModel = viewModel, openOverviewRequest = overviewRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteOverviewRequest(intent)
    }

    private fun noteOverviewRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_OVERVIEW, false) == true) openOverviewRequest.intValue++
    }

    companion object {
        const val EXTRA_OPEN_OVERVIEW = "org.jarsi.devicewatch.extra.OPEN_OVERVIEW"

        /** Opens the app on the Overview, where every quick figure is shown in full. */
        fun openOverviewIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_OVERVIEW, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
