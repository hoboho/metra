package ir.metra.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Project-wide design primitives.
 *
 * Every screen builds buttons, fields and choice chips from these so the whole
 * app shares one button hierarchy, one field style (with a leading-icon slot)
 * and one chip style — instead of each screen inventing its own.
 */

/** The single button hierarchy used everywhere in the app. */
enum class MetraButtonLevel {
    /** Main call to action (filled). */
    Primary,

    /** Secondary action (tonal). */
    Secondary,

    /** Tertiary / alternative action (outlined). */
    Outline,

    /** Low-emphasis inline action (text). */
    Text,

    /** Destructive action such as delete (error-coloured). */
    Danger,
}

/**
 * The one button. Pick a [level] instead of reaching for a raw Material button,
 * so spacing, shape, icon placement and the loading state stay identical across
 * the app.
 */
@Composable
fun MetraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: MetraButtonLevel = MetraButtonLevel.Primary,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
) {
    val effective = if (fullWidth) modifier.fillMaxWidth() else modifier
    val active = enabled && !loading
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = when (level) {
                    MetraButtonLevel.Primary, MetraButtonLevel.Danger -> MaterialTheme.colorScheme.onPrimary
                    MetraButtonLevel.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
                    MetraButtonLevel.Outline, MetraButtonLevel.Text -> MaterialTheme.colorScheme.primary
                },
            )
            if (text.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(text)
            }
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text)
        }
    }
    when (level) {
        MetraButtonLevel.Primary ->
            Button(onClick = onClick, enabled = active, modifier = effective, content = content)

        MetraButtonLevel.Secondary ->
            FilledTonalButton(onClick = onClick, enabled = active, modifier = effective, content = content)

        MetraButtonLevel.Outline ->
            OutlinedButton(onClick = onClick, enabled = active, modifier = effective, content = content)

        MetraButtonLevel.Text ->
            TextButton(onClick = onClick, enabled = active, modifier = effective, content = content)

        MetraButtonLevel.Danger ->
            Button(
                onClick = onClick,
                enabled = active,
                modifier = effective,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                content = content,
            )
    }
}

/**
 * The standard text field: outlined, with an optional leading icon, matching
 * [MetraNumberField]'s colours and shape so text and number fields look alike.
 */
@Composable
fun MetraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    placeholder: String = "",
    suffix: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        suffix = suffix?.let { { Text(it) } },
        isError = isError,
        supportingText = errorMessage?.let { { Text(it) } },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Single-select chip row. Wraps onto multiple lines, so it suits both short
 * category lists and longer "reason" lists on small screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    labelOf: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(labelOf(option)) },
            )
        }
    }
}
