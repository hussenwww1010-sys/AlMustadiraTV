
package com.almustadira.tv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var channelList: LinearLayout
    private lateinit var statusText: TextView

    private val executor = Executors.newSingleThreadExecutor()

    private val bg = Color.rgb(6, 9, 24)
    private val cyan = Color.rgb(0, 229, 255)
    private val purple = Color.rgb(168, 85, 247)
    private val panel = Color.rgb(17, 10, 44)

    private var channels: List<Channel> = emptyList()
    private var isLoading = false

    private val dataUrl =
        "https://hussenwww1010-sys.github.io/AlMustadiraTV/data.json"

    data class Channel(
        val name: String,
        val url: String,
        val type: String,
        val category: String,
        val quality: String,
        val userAgent: String,
        val referer: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        player = ExoPlayer.Builder(this).build()

        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        player.addListener(object : Player.Listener {

            override fun onPlayerError(error: PlaybackException) {
                if (isFinishing || isDestroyed) return

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
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

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    statusText.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "جاري تحميل البث..."
                        Player.STATE_READY -> "البث جاهز"
                        Player.STATE_ENDED -> "انتهى البث"
                        else -> {
                            if (channels.isEmpty()) {
                                "بانتظار القنوات..."
                            } else {
                                "المستديرة TV"
                            }
                        }
                    }
                }
            }
        })

        buildInterface()
        loadChannels()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(16, 12, 16, 12)
        }

        val title = TextView(this).apply {
            text = "المستديرة TV"
            textSize = 25f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 10)
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        statusText = TextView(this).apply {
            text = "جاري تحميل القنوات..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 10)
        }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
        }

        channelList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(2, 2, 8, 2)
        }

        val channelScroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            addView(
                channelList,
                ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
                )
            )
        }

        content.addView(
            channelScroll,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        content.addView(
            playerView,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                2f
            )
        )

        root.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val reload = TextView(this).apply {
            text = "↻ تحديث القنوات"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setPadding(12, 12, 12, 12)
            isFocusable = true
            isClickable = true

            setOnClickListener {
                loadChannels()
            }

            setOnFocusChangeListener { view, focused ->
                view.setBackgroundColor(
                    if (focused) purple else Color.TRANSPARENT
                )
            }
        }

        root.addView(
            reload,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun loadChannels() {
        if (isLoading || isFinishing || isDestroyed) return

        isLoading = true
        statusText.text = "جاري تحميل القنوات..."

        executor.execute {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(dataUrl).openConnection()
                    as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty(
                    "Cache-Control",
                    "no-cache"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    error("HTTP $responseCode")
                }

                val jsonText = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val array = JSONObject(jsonText)
                    .optJSONArray("channels")
                    ?: error("ملف data.json لا يحتوي على channels")

                val loaded = mutableListOf<Channel>()

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue

                    if (!item.optBoolean("enabled", true)) {
                        continue
                    }

                    val url = item.optString("url", "").trim()

                    if (
                        !url.startsWith("https://", true) &&
                        !url.startsWith("http://", true)
                    ) {
                        continue
                    }

                    loaded.add(
                        Channel(
                            name = item.optString(
                                "name",
                                "قناة ${i + 1}"
                            ),
                            url = url,
                            type = item.optString(
                                "type",
                                "hls"
                            ).trim().lowercase(),
                            category = item.optString(
                                "category",
                                "رياضية"
                            ),
                            quality = item.optString(
                                "quality",
                                ""
                            ),
                            userAgent = item.optString(
                                "userAgent",
                                ""
                            ),
                            referer = item.optString(
                                "referer",
                                ""
                            )
                        )
                    )
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    channels = loaded
                    isLoading = false
                    showChannels()

                    statusText.text = if (loaded.isEmpty()) {
                        "لا توجد قنوات بروابط صالحة"
                    } else {
                        "تم تحميل ${loaded.size} قناة"
                    }
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

                    showChannels()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showChannels() {
        channelList.removeAllViews()

        if (channels.isEmpty()) {
            channelList.addView(
                TextView(this).apply {
                    text = "لا توجد قنوات متاحة"
                    textSize = 15f
                    setTextColor(Color.LTGRAY)
                    gravity = Gravity.CENTER
                    setPadding(8, 20, 8, 20)
                }
            )
            return
        }

        channels.forEach { channel ->
            val button = TextView(this).apply {
                text = buildString {
                    append(channel.name)

                    if (channel.quality.isNotBlank()) {
                        append("\n")
                        append(channel.quality)
                    }
                }

                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(panel)
                setPadding(10, 14, 10, 14)
                isFocusable = true
                isClickable = true

                setOnClickListener {
                    playChannel(channel)
                }

                setOnFocusChangeListener { view, focused ->
                    view.setBackgroundColor(
                        if (focused) purple else panel
                    )
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }

            channelList.addView(button, params)
        }
    }

    private fun playChannel(channel: Channel) {
        if (channel.url.isBlank()) {
            Toast.makeText(
                this,
                "رابط القناة غير موجود",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mimeType = when (channel.type) {
            "hls", "m3u8" -> MimeTypes.APPLICATION_M3U8
            "dash", "mpd" -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        try {
            val mediaItem = MediaItem.Builder()
                .setUri(channel.url)
                .apply {
                    if (mimeType != null) {
                        setMimeType(mimeType)
                    }
                }
                .build()

            val headers = mutableMapOf<String, String>()

            if (channel.referer.isNotBlank()) {
                headers["Referer"] = channel.referer
            }

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(
                    channel.userAgent.ifBlank {
                        Util.getUserAgent(
                            this,
                            "AlMustadiraTV"
                        )
                    }
                )
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)

            val dataSourceFactory =
                DefaultDataSource.Factory(this, httpFactory)

            val mediaSourceFactory =
                DefaultMediaSourceFactory(dataSourceFactory)

            player.stop()
            player.clearMediaItems()

            player.setMediaSource(
                mediaSourceFactory.createMediaSource(mediaItem)
            )

            player.prepare()
            player.playWhenReady = true

            statusText.text = "جاري تشغيل ${channel.name}"

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر تشغيل القناة: تحقق من رابط البث",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_BACK &&
            ::playerView.isInitialized &&
            playerView.isControllerFullyVisible
        ) {
            playerView.hideController()
            return true
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
