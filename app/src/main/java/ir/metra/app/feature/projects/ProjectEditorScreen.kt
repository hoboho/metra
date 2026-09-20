package ir.metra.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.ui.components.MetraNumberField
import ir.metra.app.ui.components.SectionCard

/** Create or edit a project. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    projectId: Long,
    onDone: () -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) { viewModel.load(projectId) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.id == 0L) {
                            stringResource(R.string.projects_new)
                        } else {
                            stringResource(R.string.projects_edit)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.projects_title)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { value -> viewModel.onFieldChange { it.copy(name = value) } },
                    label = { Text(stringResource(R.string.project_name)) },
                    isError = state.errorMessage != null,
                    supportingText = state.errorMessage?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.employer,
                    onValueChange = { value -> viewModel.onFieldChange { it.copy(employer = value) } },
                    label = { Text(stringResource(R.string.project_employer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.workArea,
                    onValueChange = { value -> viewModel.onFieldChange { it.copy(workArea = value) } },
                    label = { Text(stringResource(R.string.project_work_area)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.defaultSupervisor,
                    onValueChange = { value -> viewModel.onFieldChange { it.copy(defaultSupervisor = value) } },
                    label = { Text(stringResource(R.string.project_default_supervisor)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                MetraNumberField(
                    value = state.defaultWorkerCount,
                    onValueChange = { value -> viewModel.onFieldChange { it.copy(defaultWorkerCount = value) } },
                    label = stringResource(R.string.project_default_worker_count),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { value -> viewModel.onFieldChange { it.copy(notes = value) } },
                    label = { Text(stringResource(R.string.project_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.project_active), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = state.isActive,
                        onCheckedChange = { value -> viewModel.onFieldChange { it.copy(isActive = value) } },
                    )
                }
            }

            Button(
                onClick = { viewModel.save() },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
