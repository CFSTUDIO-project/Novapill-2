package app.novapill.android.overlay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.novapill.android.overlay.IslandCoordinator

/** Content of the overlay window: renders whatever the coordinator last published. */
@Composable
fun IslandHost(coordinator: IslandCoordinator) {
    val ui by coordinator.ui.collectAsState()
    IslandRoot(ui, coordinator)
}
