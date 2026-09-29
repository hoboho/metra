package ir.metra.app.feature.widget

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ir.metra.app.MainActivity
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.domain.repository.WorkRecordRepository
import java.time.LocalDate

/**
 * 4x1 home-screen widget: today's logged meters, tap to jump straight into the
 * work editor with today prefilled.
 *
 * Glance renders with Compose; the day's figure is read straight from Room when
 * the widget (re)composes, so there is no separate cache to drift out of sync.
 */
class MetraWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, MetraWidgetEntryPoint::class.java)
        val meters = (entry.workRecordRepository().getRecordOn(LocalDate.now().toEpochDay())?.dailyMeters ?: 0).toLong()
        val text = entry.numberFormatter().formatPersian(meters)
        provideContent { WidgetUi(text) }
    }
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WidgetUi(metersText: String) {
    val context = LocalContext.current
    // Reuse the app's own editor shortcut so the widget and any in-app shortcut
    // always carry the exact same "open today" extra.
    val extras = remember { MainActivity.openWorkEditor(context).extras ?: Bundle() }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity(MainActivity::class.java, actionParametersOf(), extras)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("امروز", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Text(
                text = metersText,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text("متر", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        }
    }
}

@AndroidEntryPoint
class MetraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MetraWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MetraWidgetEntryPoint {
    fun workRecordRepository(): WorkRecordRepository
    fun numberFormatter(): NumberFormatter
}
