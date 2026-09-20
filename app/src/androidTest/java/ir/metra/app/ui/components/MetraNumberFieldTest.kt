package ir.metra.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The meter input field.
 *
 * **Requires a device or emulator.**
 *
 * This is the single most-used control in the app, so its two behaviours are
 * pinned: what it shows is Persian digits, and what it hands back to the
 * ViewModel is normalised Latin digits. If either breaks, saving stops working.
 */
@RunWith(AndroidJUnit4::class)
class MetraNumberFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an entered value is displayed with persian digits`() {
        composeRule.setContent {
            MaterialTheme {
                MetraNumberField(value = "520", onValueChange = {}, label = "کارکرد روزانه")
            }
        }
        composeRule.onNodeWithText("۵۲۰").assertIsDisplayed()
    }

    @Test
    fun `the label is displayed`() {
        composeRule.setContent {
            MaterialTheme {
                MetraNumberField(value = "", onValueChange = {}, label = "کارکرد روزانه")
            }
        }
        composeRule.onNodeWithText("کارکرد روزانه").assertIsDisplayed()
    }

    @Test
    fun `typing latin digits is passed through unchanged`() {
        var received by mutableStateOf("")
        composeRule.setContent {
            MaterialTheme {
                MetraNumberField(value = received, onValueChange = { received = it }, label = "کارکرد")
            }
        }
        composeRule.onNodeWithText("").performTextInput("520")
        assertThat(received).isEqualTo("520")
    }

    @Test
    fun `typing persian digits is normalised to latin before reaching the view model`() {
        var received by mutableStateOf("")
        composeRule.setContent {
            MaterialTheme {
                MetraNumberField(value = received, onValueChange = { received = it }, label = "کارکرد")
            }
        }
        composeRule.onNodeWithText("").performTextInput("۵۲۰")
        // The ViewModel must never see Persian digits, or parsing fails.
        assertThat(received).isEqualTo("520")
    }

    @Test
    fun `an error message is displayed when the field is invalid`() {
        composeRule.setContent {
            MaterialTheme {
                MetraNumberField(
                    value = "",
                    onValueChange = {},
                    label = "کارکرد روزانه",
                    isError = true,
                    errorMessage = "کارکرد روزانه الزامی است",
                )
            }
        }
        composeRule.onNodeWithText("کارکرد روزانه الزامی است").assertIsDisplayed()
    }
}
