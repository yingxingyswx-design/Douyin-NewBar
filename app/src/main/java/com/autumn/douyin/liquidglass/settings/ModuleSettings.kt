package com.autumn.douyin.liquidglass.settings

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.autumn.douyin.liquidglass.ModuleLog
import java.io.File
import java.io.StringWriter
import java.util.Properties
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Default theme: the dark state is pure black, the light state is paper white. */
val DefaultDarkColor: Int = 0xFF0B0B0D.toInt()
val DefaultLightColor: Int = 0xFFF7F7F8.toInt()

/**
 * Bar colors are split into two states.
 *
 * Douyin's bottom bar has four tabs: 首页 / 朋友 / 消息 / 我. The first two use the
 * dark state, the last two use the light state. Only the container color is stored;
 * content color is derived from luminance so text and icons always stay legible.
 */
data class ModuleSettings(
    val enabled: Boolean = true,
    val darkColor: Int = DefaultDarkColor,
    val lightColor: Int = DefaultLightColor,
    val barHeightDp: Int = DefaultBarHeightDp,
    val bottomGapDp: Int = DefaultBottomGapDp,
    val liquidGlass: Boolean = false,
    val frostDp: Int = DefaultFrostDp,
    val glassTintEnabled: Boolean = true,
    val realtimeGlass: Boolean = false,
    val rootCapture: Boolean = false,
    val revision: Long = 0L,
) {
    /** Window height = bar + floating gap + a small top breathing area. */
    val windowHeightDp: Int
        get() = barHeightDp + bottomGapDp + WindowTopPaddingDp

    fun barColorFor(tabIndex: Int): Int = if (tabIndex <= FriendsTabIndex) darkColor else lightColor

    fun withRevision(nextRevision: Long): ModuleSettings = copy(revision = nextRevision)

    companion object {
        const val FriendsTabIndex = 1
        const val DefaultBarHeightDp = 34
        const val DefaultBottomGapDp = 4
        const val MinBarHeightDp = 26
        const val MaxBarHeightDp = 46
        const val MinBottomGapDp = 0
        const val MaxBottomGapDp = 16
        const val WindowTopPaddingDp = 6
        const val DefaultFrostDp = 20
        const val MinFrostDp = 0
        const val MaxFrostDp = 32

        val Default = ModuleSettings()
    }
}

object ModuleSettingsStore {
    const val Authority = "com.autumn.douyin.liquidglass.settings"
    val SettingsUri: Uri = Uri.parse("content://$Authority/settings")

    const val PreferencesName = "m3_bar_settings"

    const val KeyEnabled = "enabled"
    const val KeyDarkColor = "dark_color"
    const val KeyLightColor = "light_color"
    const val KeyBarHeight = "bar_height_dp"
    const val KeyBottomGap = "bottom_gap_dp"
    const val KeyLiquidGlass = "liquid_glass"
    const val KeyFrost = "frost_dp"
    const val KeyGlassTint = "glass_tint_enabled"
    const val KeyRealtimeGlass = "realtime_glass"
    const val KeyRootCapture = "root_capture"
    const val KeySchema = "schema_version"
    const val CurrentSchema = 2
    const val KeyRevision = "revision"

    const val ColumnEnabled = "enabled"
    const val ColumnDarkColor = "dark_color"
    const val ColumnLightColor = "light_color"
    const val ColumnBarHeight = "bar_height_dp"
    const val ColumnBottomGap = "bottom_gap_dp"
    const val ColumnLiquidGlass = "liquid_glass"
    const val ColumnFrost = "frost_dp"
    const val ColumnGlassTint = "glass_tint_enabled"
    const val ColumnRealtimeGlass = "realtime_glass"
    const val ColumnRootCapture = "root_capture"
    const val ColumnRevision = "revision"

    private val revisionSource = AtomicLong(0L)

    fun read(context: Context): ModuleSettings {
        val preferences = preferences(context)
        val settings = ModuleSettings(
            enabled = preferences.getBoolean(KeyEnabled, true),
            darkColor = preferences.getInt(KeyDarkColor, DefaultDarkColor),
            lightColor = preferences.getInt(KeyLightColor, DefaultLightColor),
            barHeightDp = preferences.getInt(
                KeyBarHeight,
                ModuleSettings.DefaultBarHeightDp,
            ),
            bottomGapDp = preferences.getInt(
                KeyBottomGap,
                ModuleSettings.DefaultBottomGapDp,
            ),
            liquidGlass = preferences.getBoolean(KeyLiquidGlass, false),
            frostDp = preferences.getInt(KeyFrost, ModuleSettings.DefaultFrostDp),
            glassTintEnabled = preferences.getBoolean(KeyGlassTint, true),
            // Migration: older builds shipped the experimental 60fps path as
            // default. Force it off once so the stable static glass is used.
            realtimeGlass = preferences.getInt(KeySchema, 0) >= CurrentSchema &&
                preferences.getBoolean(KeyRealtimeGlass, false),
            // Root capture is disabled on Android 16+: the required hidden
            // SurfaceControl/ScreenCapture APIs no longer exist.
            rootCapture = false,
            revision = preferences.getLong(KeyRevision, 0L),
        )
        revisionSource.updateAndGet { current -> maxOf(current, settings.revision) }
        return settings
    }

    fun write(context: Context, settings: ModuleSettings): ModuleSettings {
        val next = settings.withRevision(nextRevision())
        preferences(context).edit()
            .putBoolean(KeyEnabled, next.enabled)
            .putInt(KeyDarkColor, next.darkColor)
            .putInt(KeyLightColor, next.lightColor)
            .putInt(KeyBarHeight, next.barHeightDp)
            .putInt(KeyBottomGap, next.bottomGapDp)
            .putBoolean(KeyLiquidGlass, next.liquidGlass)
            .putInt(KeyFrost, next.frostDp)
            .putBoolean(KeyGlassTint, next.glassTintEnabled)
            .putBoolean(KeyRealtimeGlass, next.realtimeGlass)
            .putBoolean(KeyRootCapture, next.rootCapture)
            .putInt(KeySchema, CurrentSchema)
            .putLong(KeyRevision, next.revision)
            .apply()
        context.contentResolver.notifyChange(SettingsUri, null)
        return next
    }

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun cursor(settings: ModuleSettings): Cursor = MatrixCursor(
        arrayOf(
            ColumnEnabled,
            ColumnDarkColor,
            ColumnLightColor,
            ColumnBarHeight,
            ColumnBottomGap,
            ColumnLiquidGlass,
            ColumnFrost,
            ColumnGlassTint,
            ColumnRealtimeGlass,
            ColumnRootCapture,
            ColumnRevision,
        )
    ).apply {
        addRow(
            listOf(
                settings.enabled,
                settings.darkColor,
                settings.lightColor,
                settings.barHeightDp,
                settings.bottomGapDp,
                settings.liquidGlass,
                settings.frostDp,
                settings.glassTintEnabled,
                settings.realtimeGlass,
                settings.rootCapture,
                settings.revision,
            )
        )
    }

    fun fromCursor(cursor: Cursor): ModuleSettings? = cursor.use { current ->
        val enabled = current.getColumnIndex(ColumnEnabled)
        val dark = current.getColumnIndex(ColumnDarkColor)
        val light = current.getColumnIndex(ColumnLightColor)
        val barHeight = current.getColumnIndex(ColumnBarHeight)
        val bottomGap = current.getColumnIndex(ColumnBottomGap)
        val liquidGlass = current.getColumnIndex(ColumnLiquidGlass)
        val frost = current.getColumnIndex(ColumnFrost)
        val glassTint = current.getColumnIndex(ColumnGlassTint)
        val realtime = current.getColumnIndex(ColumnRealtimeGlass)
        val rootCapture = current.getColumnIndex(ColumnRootCapture)
        val revision = current.getColumnIndex(ColumnRevision)
        if (!current.moveToFirst() ||
            enabled < 0 || dark < 0 || light < 0 ||
            barHeight < 0 || bottomGap < 0 || liquidGlass < 0 ||
            frost < 0 || glassTint < 0 || realtime < 0 ||
            rootCapture < 0 || revision < 0
        ) {
            return null
        }
        ModuleSettings(
            enabled = current.getInt(enabled) != 0,
            darkColor = current.getInt(dark),
            lightColor = current.getInt(light),
            barHeightDp = current.getInt(barHeight),
            bottomGapDp = current.getInt(bottomGap),
            liquidGlass = current.getInt(liquidGlass) != 0,
            frostDp = current.getInt(frost),
            glassTintEnabled = current.getInt(glassTint) != 0,
            realtimeGlass = current.getInt(realtime) != 0,
            rootCapture = false,
            revision = current.getLong(revision),
        )
    }

    fun contentValues(settings: ModuleSettings): ContentValues = ContentValues().apply {
        put(ColumnEnabled, settings.enabled)
        put(ColumnDarkColor, settings.darkColor)
        put(ColumnLightColor, settings.lightColor)
        put(ColumnBarHeight, settings.barHeightDp)
        put(ColumnBottomGap, settings.bottomGapDp)
        put(ColumnLiquidGlass, settings.liquidGlass)
        put(ColumnFrost, settings.frostDp)
        put(ColumnGlassTint, settings.glassTintEnabled)
        put(ColumnRealtimeGlass, settings.realtimeGlass)
        put(ColumnRootCapture, settings.rootCapture)
        put(ColumnRevision, settings.revision)
    }

    fun serialize(settings: ModuleSettings): String {
        val properties = Properties().apply {
            setProperty(KeyEnabled, settings.enabled.toString())
            setProperty(KeyDarkColor, settings.darkColor.toString())
            setProperty(KeyLightColor, settings.lightColor.toString())
            setProperty(KeyBarHeight, settings.barHeightDp.toString())
            setProperty(KeyBottomGap, settings.bottomGapDp.toString())
            setProperty(KeyLiquidGlass, settings.liquidGlass.toString())
            setProperty(KeyFrost, settings.frostDp.toString())
            setProperty(KeyGlassTint, settings.glassTintEnabled.toString())
            setProperty(KeyRealtimeGlass, settings.realtimeGlass.toString())
            setProperty(KeyRootCapture, settings.rootCapture.toString())
            setProperty(KeyRevision, settings.revision.toString())
        }
        val writer = StringWriter()
        properties.store(writer, null)
        return writer.toString()
    }

    fun deserialize(value: String): ModuleSettings? = runCatching {
        val properties = Properties()
        properties.load(value.reader())
        ModuleSettings(
            enabled = properties.getProperty(KeyEnabled)?.toBooleanStrictOrNull() ?: true,
            darkColor = properties.getProperty(KeyDarkColor)?.toIntOrNull() ?: DefaultDarkColor,
            lightColor = properties.getProperty(KeyLightColor)?.toIntOrNull() ?: DefaultLightColor,
            barHeightDp = properties.getProperty(KeyBarHeight)?.toIntOrNull()
                ?: ModuleSettings.DefaultBarHeightDp,
            bottomGapDp = properties.getProperty(KeyBottomGap)?.toIntOrNull()
                ?: ModuleSettings.DefaultBottomGapDp,
            liquidGlass = properties.getProperty(KeyLiquidGlass)?.toBooleanStrictOrNull() ?: false,
            frostDp = properties.getProperty(KeyFrost)?.toIntOrNull()
                ?: ModuleSettings.DefaultFrostDp,
            glassTintEnabled = properties.getProperty(KeyGlassTint)
                ?.toBooleanStrictOrNull() ?: true,
            realtimeGlass = properties.getProperty(KeyRealtimeGlass)
                ?.toBooleanStrictOrNull() ?: false,
            rootCapture = false,
            revision = properties.getProperty(KeyRevision)?.toLongOrNull() ?: return null,
        )
    }.getOrNull()

    private fun nextRevision(): Long {
        while (true) {
            val current = revisionSource.get()
            val next = System.currentTimeMillis()
            if (next > current && revisionSource.compareAndSet(current, next)) return next
            if (revisionSource.compareAndSet(current, current + 1)) return current + 1
        }
    }
}

/**
 * Root file mirror.
 *
 * The module app writes settings into Douyin's own external files directory with su;
 * the hooked Douyin process can always read that file with its own uid. This is the
 * same directory strategy the original liquid-glass module used, and it is the most
 * reliable channel on OEM ROMs where provider visibility is filtered.
 */
object ModuleSettingsMirror {
    private const val DouyinPackage = "com.ss.android.ugc.aweme"
    private const val RelativePath = "files/m3-bar/settings.properties"

    fun targetFile(): File = File(
        "/storage/emulated/0/Android/data/$DouyinPackage/$RelativePath",
    )

    fun write(context: Context, settings: ModuleSettings): Boolean = runCatching {
        val temporary = File(context.cacheDir, "m3-bar-settings.properties")
        temporary.writeText(ModuleSettingsStore.serialize(settings), Charsets.UTF_8)
        val target = targetFile()
        val directory = target.parentFile ?: return false
        val command = listOf(
            "/system/bin/mkdir",
            "-p",
            shellQuote(directory.absolutePath),
            "&&",
            "/system/bin/cp",
            shellQuote(temporary.absolutePath),
            shellQuote(target.absolutePath),
            "&&",
            "/system/bin/chmod",
            "644",
            shellQuote(target.absolutePath),
        ).joinToString(" ")
        val process = ProcessBuilder("/system/bin/su", "-c", command)
            .redirectErrorStream(true)
            .start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    }.onFailure {
        ModuleLog.error("failed to write settings mirror", it)
    }.getOrDefault(false)

    fun read(): ModuleSettings? = runCatching {
        val file = targetFile()
        if (!file.isFile) return null
        ModuleSettingsStore.deserialize(file.readText(Charsets.UTF_8))
    }.onFailure {
        ModuleLog.error("failed to read settings mirror", it)
    }.getOrNull()

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}

/**
 * Reads settings inside the Douyin process.
 *
 * Channels are evaluated by highest revision:
 * 1. ContentProvider (instant, no root needed for reads)
 * 2. Root file mirror in Douyin's external files directory
 * A 2 second poll backs up the ContentObserver so live edits always land.
 */
object ModuleSettingsBridge {
    private const val PollIntervalMs = 2_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "m3-bar-settings").apply { isDaemon = true }
    }
    private val listeners = CopyOnWriteArraySet<(ModuleSettings) -> Unit>()

    @Volatile
    private var currentSettings = ModuleSettings.Default
    private var contentObserver: android.database.ContentObserver? = null

    @Volatile
    private var appContext: Context? = null
    private var started = false
    private var lastErrorLogAt = 0L

    val current: ModuleSettings
        get() = currentSettings

    private val poller = object : Runnable {
        override fun run() {
            refreshAsync()
            mainHandler.postDelayed(this, PollIntervalMs)
        }
    }

    fun start(context: Context, onSettingsChanged: (ModuleSettings) -> Unit) {
        val application = context.applicationContext ?: context
        synchronized(this) {
            appContext = application
            if (started) return
            started = true
            listeners += onSettingsChanged
        }
        installObserver(application)
        // Synchronous first read: the overlay must be built with the user's real
        // height/gap/colors, not the defaults.
        refresh(application)
        mainHandler.postDelayed(poller, PollIntervalMs)
    }

    fun addListener(onSettingsChanged: (ModuleSettings) -> Unit) {
        listeners += onSettingsChanged
    }

    fun removeListener(onSettingsChanged: (ModuleSettings) -> Unit) {
        listeners -= onSettingsChanged
    }

    fun refreshAsync() {
        val context = appContext ?: return
        runCatching {
            refreshExecutor.execute {
                runCatching { refresh(context) }
                    .onFailure { ModuleLog.error("settings refresh crashed", it) }
            }
        }
    }

    fun refreshNow(context: Context) {
        val application = context.applicationContext ?: context
        appContext = application
        refresh(application)
    }

    private fun installObserver(context: Context) {
        val observer = object : android.database.ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshAsync()
            }
        }
        contentObserver = observer
        runCatching {
            context.contentResolver.registerContentObserver(
                ModuleSettingsStore.SettingsUri,
                false,
                observer,
            )
        }.onFailure { ModuleLog.error("failed to register settings observer", it) }
    }

    private fun refresh(context: Context) {
        val providerSettings = runCatching {
            context.contentResolver.query(
                ModuleSettingsStore.SettingsUri,
                null,
                null,
                null,
                null,
            )?.let(ModuleSettingsStore::fromCursor)
        }.onFailure {
            logErrorThrottled("settings provider query failed", it)
        }.getOrNull()
        val mirrorSettings = ModuleSettingsMirror.read()
        val next = listOf(providerSettings, mirrorSettings)
            .filterNotNull()
            .maxByOrNull { it.revision }
        if (next != null) applySettings(next)
    }

    private fun applySettings(settings: ModuleSettings) {
        val shouldNotify = synchronized(this) {
            val changed = settings != currentSettings
            if (changed) currentSettings = settings
            changed
        }
        if (!shouldNotify) return
        ModuleLog.info(
            "settings applied revision=${settings.revision} " +
                "height=${settings.barHeightDp} gap=${settings.bottomGapDp} " +
                "dark=${settings.darkColor} light=${settings.lightColor}",
        )
        mainHandler.post {
            listeners.forEach { listener -> listener(currentSettings) }
        }
    }

    private fun logErrorThrottled(message: String, throwable: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastErrorLogAt < 10_000L) return
        lastErrorLogAt = now
        ModuleLog.error(message, throwable)
    }
}
