package com.almustadira.tv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelRow: LinearLayout
    private lateinit var categoryRow: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowSub: TextView
    private lateinit var hero: LinearLayout
    private lateinit var playerCard: FrameLayout
    private lateinit var playerOverlay: LinearLayout
    private lateinit var scrollRoot: ScrollView
    private lateinit var fsContainer: FrameLayout
    private lateinit var playerCardParams: LinearLayout.LayoutParams
    private val aspectPills = mutableListOf<TextView>()
    private var aspectIndex = 0
    private var controllerVisible = false
    private val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "ملائم",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "تمديد",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "تكبير"
    )

    private val executor = Executors.newSingleThreadExecutor()

    // الألوان
    private val bgA = Color.rgb(6, 9, 24)
    private val bgB = Color.rgb(38, 10, 68)
    private val bgC = Color.rgb(125, 18, 72)
    private val cyan = Color.rgb(0, 229, 255)
    private val purple = Color.rgb(168, 85, 247)
    private val pink = Color.rgb(236, 72, 153)
    private val glass = Color.argb(46, 255, 255, 255)
    private val glassDark = Color.argb(90, 10, 5, 30)

    private var channels: List<Channel> = emptyList()
    private var selectedCategory = ALL
    private var currentUrl = ""
    private var isFullscreen = false
    @Volatile private var isLoading = false

    private val dataUrl =
        "https://hussenwww1010-sys.github.io/AlMustadiraTV/data.json"

    companion object {
        private const val ALL = "الكل"
    }

    data class Channel(
        val name: String,
        val url: String,
        val type: String,
        val category: String,
        val quality: String,
        val userAgent: String,
        val referer: String
    )

    // ───────────────────────── أدوات مساعدة ─────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(
        color: Int,
        radius: Int,
        stroke: Int = 0,
        strokeW: Int = 0
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeW > 0) setStroke(dp(strokeW), stroke)
    }

    private fun cardBg(focused: Boolean, selected: Boolean) = when {
        focused -> GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(purple, pink)
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(2), cyan)
        }
        selected -> shape(glass, 20, cyan, 2)
        else -> shape(glass, 20, Color.argb(40, 255, 255, 255), 1)
    }

    private fun chipBg(focused: Boolean, selected: Boolean) = when {
        focused -> shape(purple, 22, cyan, 2)
        selected -> shape(pink, 22)
        else -> shape(glass, 22)
    }

    private fun makePill(label: String, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(8), dp(18), dp(8))
            isFocusable = true
            isClickable = true
            background = shape(glass, 22)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                v.background =
                    if (f) shape(purple, 22, cyan, 2) else shape(glass, 22)
            }
        }

    private fun hScroll(row: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        clipChildren = false
        clipToPadding = false
        setPadding(dp(4), dp(10), dp(4), dp(10))
        addView(row)
    }

    // ───────────────────────── دورة الحياة ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgA
        window.navigationBarColor = bgA
        buildInterface()

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        statusText.text = "تعذر تشغيل القناة"
                        Toast.makeText(
                            this@MainActivity,
                            "تعذر تشغيل القناة. تحقق من رابط البث.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isFinishing || isDestroyed) return
                statusText.text = when (playbackState) {
                    Player.STATE_BUFFERING -> "⏳ جاري تحميل البث..."
                    Player.STATE_READY -> "🟢 البث جاهز"
                    Player.STATE_ENDED -> "انتهى البث"
                    else ->
                        if (channels.isEmpty()) "بانتظار القنوات..."
                        else "اختر قناة للمشاهدة"
                }
            }
        })
        loadChannels()
    }

    // ───────────────────────── بناء الواجهة ─────────────────────────

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(bgA, bgB, bgC)
            )
            setPadding(dp(32), dp(18), dp(32), dp(14))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            clipChildren = false
        }

        // ── الشريط العلوي ──
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "▶"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(cyan, purple)
            ).apply { cornerRadius = dp(14).toFloat() }
        }
        top.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = TextView(this).apply {
            text = "المستديرة TV"
            textSize = 26f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
        }
        top.addView(title)

        top.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        val reload = makePill("↻  تحديث القنوات") { loadChannels() }
        top.addView(reload)

        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, 0, 0)
        }
        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = 28f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE d MMMM"
            format24Hour = "EEEE d MMMM"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        clockCol.addView(clock)
        clockCol.addView(date)
        top.addView(clockCol)

        root.addView(
            top,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ── القسم الرئيسي: معلومات + المشغل ──
        hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(glassDark, 28, Color.argb(40, 255, 255, 255), 1)
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }

        info.addView(TextView(this).apply {
            text = "● يُشاهد الآن"
            textSize = 13f
            setTextColor(cyan)
        })
        nowTitle = TextView(this).apply {
            text = "المستديرة TV"
            textSize = 30f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(4))
        }
        info.addView(nowTitle)
        nowSub = TextView(this).apply {
            text = "اختر قناة من القائمة بالأسفل"
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }
        info.addView(nowSub)
        statusText = TextView(this).apply {
            text = "جاري تحميل القنوات..."
            textSize = 14f
            setTextColor(Color.rgb(200, 180, 255))
            setPadding(0, dp(14), 0, dp(14))
        }
        info.addView(statusText)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        btnRow.addView(makePill("⛶  ملء الشاشة") { setFullscreen(true) })
        val aspectPill = makePill("") { cycleAspect() }
        aspectPills.add(aspectPill)
        btnRow.addView(
            aspectPill,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
        )
        info.addView(btnRow)
        updateAspectLabels()

        hero.addView(
            info,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )

        playerView = PlayerView(this).apply {
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        playerCard = FrameLayout(this).apply {
            background = shape(Color.BLACK, 28, Color.argb(70, 0, 229, 255), 2)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            addView(
                playerView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        // أزرار فوق المشغل: ملء الشاشة + شكل الصورة
        playerOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val fsBtn = makePill("⛶") { setFullscreen(!isFullscreen) }
        val overlayAspect = makePill("") { cycleAspect() }
        aspectPills.add(overlayAspect)
        playerOverlay.addView(fsBtn)
        playerOverlay.addView(
            overlayAspect,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        )
        playerCard.addView(
            playerOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        )
        updateAspectLabels()

        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { v ->
                controllerVisible = v == View.VISIBLE
                updateOverlayVisibility()
            }
        )

        // ارتفاع ثابت للمشغل حتى لا ينضغط على الشاشات الصغيرة
        val heroHeight = (resources.displayMetrics.heightPixels * 0.45f)
            .toInt().coerceAtLeast(dp(210))

        playerCardParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            2f
        ).apply { marginStart = dp(20) }
        hero.addView(playerCard, playerCardParams)

        root.addView(
            hero,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heroHeight
            ).apply { topMargin = dp(14) }
        )

        // ── الجزء السفلي: التصنيفات + القنوات ──
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }

        categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        bottom.addView(
            hScroll(categoryRow),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        bottom.addView(TextView(this).apply {
            text = "قنواتك"
            textSize = 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(2), 0, 0)
        })

        channelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        bottom.addView(
            hScroll(channelRow),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            bottom,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        scrollRoot = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        fsContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        val outer = FrameLayout(this).apply {
            setBackgroundColor(bgA)
            addView(
                scrollRoot,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                fsContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(outer)
    }

    private fun cycleAspect() {
        aspectIndex = (aspectIndex + 1) % aspectModes.size
        playerView.resizeMode = aspectModes[aspectIndex].first
        updateAspectLabels()
    }

    private fun updateAspectLabels() {
        val label = "⤢ " + aspectModes[aspectIndex].second
        aspectPills.forEach { it.text = label }
    }

    private fun updateOverlayVisibility() {
        playerOverlay.visibility =
            if (!isFullscreen || controllerVisible) View.VISIBLE else View.GONE
    }

    private fun setFullscreen(on: Boolean) {
        if (on == isFullscreen) return
        isFullscreen = on
        if (on) {
            hero.removeView(playerCard)
            fsContainer.addView(
                playerCard,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            fsContainer.visibility = View.VISIBLE
            scrollRoot.visibility = View.GONE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            playerView.requestFocus()
        } else {
            fsContainer.removeView(playerCard)
            fsContainer.visibility = View.GONE
            hero.addView(playerCard, playerCardParams)
            scrollRoot.visibility = View.VISIBLE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            refocusSelected()
        }
        updateOverlayVisibility()
    }

    private fun refocusSelected() {
        for (i in 0 until channelRow.childCount) {
            val v = channelRow.getChildAt(i)
            if ((v.tag as? Channel)?.url == currentUrl) {
                v.requestFocus()
                return
            }
        }
        channelRow.getChildAt(0)?.requestFocus()
    }

    // ───────────────────────── تحميل القنوات ─────────────────────────

    private fun loadChannels() {
        if (isLoading || isFinishing || isDestroyed) return
        isLoading = true
        statusText.text = "جاري تحميل القنوات..."
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(dataUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("HTTP $responseCode")
                }

                val jsonText = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val array = JSONObject(jsonText).optJSONArray("channels")
                    ?: throw IllegalStateException(
                        "ملف data.json لا يحتوي على channels"
                    )

                val loaded = mutableListOf<Channel>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (!item.optBoolean("enabled", true)) continue
                    val url = item.optString("url", "").trim()
                    if (!url.startsWith("https://", true) &&
                        !url.startsWith("http://", true)
                    ) continue

                    loaded.add(
                        Channel(
                            name = item.optString("name", "قناة ${i + 1}"),
                            url = url,
                            type = item.optString("type", "hls")
                                .trim().lowercase(),
                            category = item.optString("category", "رياضية"),
                            quality = item.optString("quality", ""),
                            userAgent = item.optString("userAgent", ""),
                            referer = item.optString("referer", "")
                        )
                    )
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    channels = loaded
                    isLoading = false
                    selectedCategory = ALL
                    showCategories()
                    showChannels(focusFirst = true)
                    statusText.text =
                        if (loaded.isEmpty()) "لا توجد قنوات بروابط صالحة"
                        else "تم تحميل ${loaded.size} قناة"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isLoading = false
                    statusText.text = "تعذر تحميل القنوات"
                    Toast.makeText(
                        this@MainActivity,
                        "تعذر تحميل data.json. تحقق من الإنترنت والملف.",
                        Toast.LENGTH_LONG
                    ).show()
                    showCategories()
                    showChannels(focusFirst = false)
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    // ───────────────────────── التصنيفات ─────────────────────────

    private fun showCategories() {
        categoryRow.removeAllViews()
        val cats = listOf(ALL) +
            channels.map { it.category }.filter { it.isNotBlank() }.distinct()
        cats.forEach { cat ->
            val chip = TextView(this).apply {
                text = cat
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(8), dp(20), dp(8))
                tag = cat
                isFocusable = true
                isClickable = true
                background = chipBg(false, cat == selectedCategory)
                setOnClickListener {
                    selectedCategory = cat
                    refreshCategoryStyles()
                    showChannels(focusFirst = false)
                }
                setOnFocusChangeListener { v, f ->
                    v.background = chipBg(f, (v.tag as String) == selectedCategory)
                }
            }
            categoryRow.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(10) }
            )
        }
    }

    private fun refreshCategoryStyles() {
        for (i in 0 until categoryRow.childCount) {
            val v = categoryRow.getChildAt(i)
            v.background = chipBg(v.isFocused, (v.tag as String) == selectedCategory)
        }
    }

    // ───────────────────────── بطاقات القنوات ─────────────────────────

    private fun showChannels(focusFirst: Boolean) {
        channelRow.removeAllViews()

        val list = if (selectedCategory == ALL) channels
        else channels.filter { it.category == selectedCategory }

        if (list.isEmpty()) {
            channelRow.addView(TextView(this).apply {
                text = "لا توجد قنوات متاحة"
                textSize = 15f
                setTextColor(Color.LTGRAY)
                setPadding(dp(8), dp(30), dp(8), dp(30))
            })
            return
        }

        list.forEach { channel ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                tag = channel
                setPadding(dp(10), dp(12), dp(10), dp(12))
                isFocusable = true
                isClickable = true
                background = cardBg(false, channel.url == currentUrl)
                setOnClickListener { playChannel(channel) }
                setOnFocusChangeListener { v, f ->
                    v.animate()
                        .scaleX(if (f) 1.08f else 1f)
                        .scaleY(if (f) 1.08f else 1f)
                        .setDuration(150)
                        .start()
                    v.background = cardBg(f, channel.url == currentUrl)
                    v.elevation = if (f) dp(8).toFloat() else 0f
                }
            }

            val icon = TextView(this).apply {
                text = channel.name.trim().take(1)
                textSize = 20f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(cyan, purple)
                ).apply { shape = GradientDrawable.OVAL }
            }
            card.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

            card.addView(TextView(this).apply {
                text = channel.name
                textSize = 15f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(8), 0, 0)
            })

            if (channel.quality.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = channel.quality
                    textSize = 11f
                    setTextColor(cyan)
                    gravity = Gravity.CENTER
                    background = shape(Color.argb(60, 0, 229, 255), 10)
                    setPadding(dp(8), dp(1), dp(8), dp(1))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) })
            }

            channelRow.addView(
                card,
                LinearLayout.LayoutParams(dp(150), dp(112))
                    .apply { marginEnd = dp(14) }
            )
        }

        if (focusFirst) channelRow.getChildAt(0)?.requestFocus()
    }

    private fun refreshSelection() {
        for (i in 0 until channelRow.childCount) {
            val v = channelRow.getChildAt(i)
            val ch = v.tag as? Channel ?: continue
            v.background = cardBg(v.isFocused, ch.url == currentUrl)
        }
    }

    // ───────────────────────── التشغيل ─────────────────────────

    private fun playChannel(channel: Channel) {
        if (channel.url.isBlank()) {
            Toast.makeText(this, "رابط القناة غير موجود", Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = when (channel.type) {
            "hls", "m3u8" -> MimeTypes.APPLICATION_M3U8
            "dash", "mpd" -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        try {
            val headers = mutableMapOf<String, String>()
            if (channel.referer.isNotBlank()) {
                headers["Referer"] = channel.referer
            }

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(
                    channel.userAgent.ifBlank {
                        Util.getUserAgent(this, "AlMustadiraTV")
                    }
                )
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)

            val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val mediaItemBuilder = MediaItem.Builder().setUri(channel.url)
            if (mimeType != null) mediaItemBuilder.setMimeType(mimeType)

            val mediaSource =
                mediaSourceFactory.createMediaSource(mediaItemBuilder.build())

            player.stop()
            player.clearMediaItems()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true

            currentUrl = channel.url
            nowTitle.text = channel.name
            nowSub.text = buildString {
                append(channel.category)
                if (channel.quality.isNotBlank()) append("  •  ${channel.quality}")
            }
            statusText.text = "جاري تشغيل ${channel.name}"
            refreshSelection()
        } catch (e: Exception) {
            statusText.text = "تعذر تشغيل القناة"
            Toast.makeText(
                this,
                "تعذر تشغيل القناة: تحقق من رابط البث",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ───────────────────────── الأزرار والإغلاق ─────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isFullscreen) {
                setFullscreen(false)
                return true
            }
            if (::playerView.isInitialized && playerView.isControllerFullyVisible) {
                playerView.hideController()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            player.release()
        }
        executor.shutdownNow()
        super.onDestroy()
    }
}
