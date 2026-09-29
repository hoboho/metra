package ir.metra.app.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import ir.metra.app.ui.components.ChoiceChips
import ir.metra.app.ui.components.MetraButton
import ir.metra.app.ui.components.MetraButtonLevel
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import ir.metra.app.ui.components.StatRow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.ui.components.expenseCategoryLabel
import ir.metra.app.ui.components.MetraNumberField
import ir.metra.app.ui.components.SectionCard

/** Edit one expense line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    workRecordId: Long,
    expenseId: Long,
    onDone: () -> Unit,
    viewModel: ExpenseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    // Copies into app storage on pick; see ReceiptPhotoStore.
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.attachPhoto(uri) }

    LaunchedEffect(workRecordId, expenseId) { viewModel.load(workRecordId, expenseId) }
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
                        if (expenseId == 0L) {
                            stringResource(R.string.expense_new)
                        } else {
                            stringResource(R.string.expense_edit)
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
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(
                title = stringResource(R.string.expense_title),
                subtitle = if (state.projectName.isNotEmpty()) {
                    stringResource(R.string.expense_context_format, state.workDateLabel, state.projectName)
                } else {
                    null
                },
            ) {
                MetraNumberField(
                    value = state.amount,
                    onValueChange = { viewModel.onAmountChange(it) },
                    label = stringResource(R.string.expense_amount),
                    suffix = stringResource(R.string.toman),
                    isError = state.errorMessage != null,
                    errorMessage = state.errorMessage,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.expense_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ChoiceChips(
                    options = ExpenseCategory.entries.toList(),
                    selected = state.category,
                    onSelected = { viewModel.onCategoryChange(it) },
                    labelOf = { expenseCategoryLabel(it) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    label = { Text(stringResource(R.string.expense_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (state.receiptPhotoPath != null) {
                    val bmp = remember(state.receiptPhotoPath) {
                        android.graphics.BitmapFactory.decodeFile(state.receiptPhotoPath)
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetraButton(
                            text = stringResource(R.string.expense_change_photo),
                            onClick = {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            level = MetraButtonLevel.Outline,
                            icon = Icons.Filled.Image,
                            fullWidth = false,
                            modifier = Modifier.weight(1f),
                        )
                        MetraButton(
                            text = stringResource(R.string.expense_remove_photo),
                            onClick = { viewModel.removePhoto() },
                            level = MetraButtonLevel.Danger,
                            icon = Icons.Filled.Delete,
                            fullWidth = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    MetraButton(
                        text = stringResource(R.string.expense_attach_photo),
                        onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        level = MetraButtonLevel.Secondary,
                        icon = Icons.Filled.Image,
                    )
                }
                if (state.dayExpenseTotalLabel.isNotEmpty()) {
                    StatRow(stringResource(R.string.expense_day_total), state.dayExpenseTotalLabel)
                }
            }
            MetraButton(
                text = stringResource(R.string.action_save),
                onClick = { viewModel.save() },
                loading = state.saving,
            )
            if (expenseId != 0L) {
                MetraButton(
                    text = stringResource(R.string.expense_delete),
                    onClick = { viewModel.delete() },
                    level = MetraButtonLevel.Danger,
                    icon = Icons.Filled.Delete,
                )
            }
        }
    }
}

