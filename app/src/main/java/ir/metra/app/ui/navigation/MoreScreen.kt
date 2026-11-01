package ir.metra.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.metra.app.R
import ir.metra.app.ui.components.MetraButton
import ir.metra.app.ui.components.MetraButtonLevel
import ir.metra.app.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenStatistics: () -> Unit,
    onOpenShooting: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.more_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetraButton(
                            text = stringResource(R.string.more_statistics),
                            onClick = onOpenStatistics,
                            level = MetraButtonLevel.Outline,
                            icon = Icons.Default.BarChart,
                        )
                        MetraButton(
                            text = stringResource(R.string.more_shooting),
                            onClick = onOpenShooting,
                            level = MetraButtonLevel.Outline,
                            icon = Icons.Default.Build,
                        )
                        MetraButton(
                            text = stringResource(R.string.more_settings),
                            onClick = onOpenSettings,
                            level = MetraButtonLevel.Outline,
                            icon = Icons.Default.Settings,
                        )
                    }
                }
            }
        }
    }
}
