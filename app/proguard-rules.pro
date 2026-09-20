# METRA — release shrink/obfuscation rules.
#
# AGP already supplies proguard-android-optimize.txt plus the consumer rules
# shipped inside each AndroidX artifact (Room, Hilt, Work, Biometric), so only
# what those do not cover belongs here. Every rule below is justified in a
# comment; speculative keep rules are not allowed because they silently defeat
# shrinking.

# ---------------------------------------------------------------------------
# kotlinx.serialization
#
# The generated `serializer()` companions are looked up reflectively by
# `Json.encodeToString<T>()` when the reified overload cannot resolve the
# descriptor at compile time. BackupPayload and the report DTOs are serialised
# that way, so the serialisers and their no-arg constructors must survive.
# ---------------------------------------------------------------------------
-keepclassmembers class ir.metra.app.core.backup.**$$serializer { *; }
-keepclassmembers class ir.metra.app.domain.report.**$$serializer { *; }
-keepclasseswithmembers class ir.metra.app.core.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ir.metra.app.domain.report.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Room
#
# Entities are instantiated by generated code via their constructor; the cursor
# projection reads column names reflectively against the entity's field names.
# Renaming those fields would silently break a restored database.
# ---------------------------------------------------------------------------
-keep class ir.metra.app.data.local.**Entity { *; }
-keep class ir.metra.app.data.local.WorkRecordWithExpenses { *; }
-keep class ir.metra.app.data.local.RangeSummaryRow { *; }
-keep class ir.metra.app.data.local.MonthSummaryRow { *; }
-keep class ir.metra.app.data.local.ProjectSummaryRow { *; }
-keep class ir.metra.app.data.local.TotalsColumns { *; }

# ---------------------------------------------------------------------------
# PersianDate
#
# A pure-Java Jalali calendar. It is reached through static helpers only, so no
# reflection rule is needed — but its month-name tables are string constants
# that must not be inlined away by the optimiser, and the library is not
# annotated for shrinking.
# ---------------------------------------------------------------------------
-keep class saman.zamani.persiandate.PersianDate { *; }
-dontwarn saman.zamani.persiandate.**

# ---------------------------------------------------------------------------
# javax.annotation (pulled in transitively; absent at runtime on Android)
# ---------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ---------------------------------------------------------------------------
# Keep line numbers in crash reports.
#
# The app is offline-first with no analytics, so a stack trace only ever reaches
# a developer reading logcat — but an obfuscated trace without line numbers is
# close to useless there. Name obfuscation stays on.
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Kotlin metadata is required by Hilt/Dagger and kotlinx.serialization at
# runtime; AGP's defaults keep most of it, but the annotation is cheap insurance
# and costs nothing measurable.
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
