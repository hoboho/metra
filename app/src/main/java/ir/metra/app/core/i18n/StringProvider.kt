package ir.metra.app.core.i18n

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves user-facing strings from resources for non-Compose code.
 *
 * ViewModels and domain services cannot call `stringResource`, and the product
 * rule is that no UI text is baked into Kotlin. This is the seam: anything that
 * produces a message the user reads goes through here so the text stays in
 * `strings.xml` and remains translatable.
 *
 * It is an interface rather than a direct `Context` dependency so unit tests can
 * supply a fixed catalogue.
 */
interface StringProvider {

    fun string(@StringRes id: Int): String

    fun string(@StringRes id: Int, vararg args: Any?): String
}

@Singleton
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringProvider {

    override fun string(@StringRes id: Int): String = context.getString(id)

    override fun string(@StringRes id: Int, vararg args: Any?): String = context.getString(id, *args)
}
