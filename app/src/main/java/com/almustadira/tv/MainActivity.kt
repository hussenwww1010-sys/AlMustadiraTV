
package com.almustadira.tv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
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

    data class Channel(
        val name: String,
        val url: String,
        val type: String,
        val category: String,
        val quality: String,
        val userAgent: String,
        val referer: String
    )

    private var channels: List<Channel> = emptyList()

    private val dataUrl =
        "https://hussenwww1010-sys.github.io/AlMustadiraTV/data.json"

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
        }

        buildInterface()
        loadChannels()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "تعذر تشغيل القناة. تحقق من رابط البث.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(16, 12, 16, 12)
        }

        root.addView(TextView(this).apply {
            text = "المستديرة TV"
            textSize = 25f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 10)
        })

        statusText = TextView(this).apply {
            text = "جاري تحميل القنوات..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 10)
        }
        root.addView(statusText)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        channelList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(
            channelList,
            LinearLayout.LayoutParams(0, 1, 1f)
        )
        content.addView(
            playerView,
            LinearLayout.LayoutParams(0, 1, 2f)
        )

        root.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val reload = TextView(this).apply {
            text = "↻ تحديث القنوات"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setPadding(12, 12, 12, 12)
            isFocusable = true
            setOnClickListener { loadChannels() }
            setOnFocusChangeListener { view, focused ->
                view.setBackgroundColor(
                    if (focused) purple else Color.TRANSPARENT
                )
            }
        }
        root.addView(reload)

        setContentView(root)
    }

    private fun loadChannels() {
        statusText.text = "جاري تحميل القنوات..."

        executor.execute {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(dataUrl).openConnection()
                    as HttpURLConnection

                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.setRequestProperty(
                    "Cache-Control", "no-cache"
                )

                if (connection.responseCode !in 200..299) {
                    error("HTTP ${connection.responseCode}")
                }

                val text = connection.inputStream
                    .bufferedReader(Charsets.UTF_8).use {
                        it.readText()
                    }

                val array = JSONObject(text).optJSONArray("channels")
                    ?: error("Missing channels array")

                val loaded = mutableListOf<Channel>()

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (!item.optBoolean("enabled", true)) continue

                    loaded.add(
                        Channel(
                            name = item.optString(
                                "name", "قناة ${i + 1}"
                            ),
                            url = item.optString("url", "").trim(),
                            type = item.optString("type", "hls")
                                .lowercase(),
                            category = item.optString(
                                "category", "رياضية"
                            ),
                            quality = item.optString("quality", ""),
                            userAgent = item.optString("userAgent", ""),
                            referer = item.optString("referer", "")
                        )
                    )
                }

                runOnUiThread {
                    channels = loaded
                    showChannels()
                    statusText.text =
                        "تم تحميل ${loaded.size} قناة"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "تعذر تحميل القنوات"
                    Toast.makeText(
                        this@MainActivity,
                        "تحقق من رابط data.json والإنترنت",
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
            channelList.addView(TextView(this).apply {
                text = "لا توجد قنوات محمّلة"
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(8, 20, 8, 20)
            })
            return
        }

        channels.forEach { channel ->
            val button = TextView(this).apply {
                text = channel.name +
                    if (channel.quality.isNotBlank())
                        "\n${channel.quality}" else ""
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(panel)
                setPadding(10, 14, 10, 14)
                isFocusable = true
                isClickable = true
                setOnClickListener { playChannel(channel) }
                setOnFocusChangeListener { view, focused ->
                    view.setBackgroundColor(
                        if (focused) purple else panel
                    )
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            channelList.addView(button, params)
        }
    }

    private fun playChannel(channel: Channel) {
        val url = channel.url

        if (!url.startsWith("https://", true) &&
            !url.startsWith("http://", true)
        ) {
            Toast.makeText(
                this,
                "أضف رابط بث صحيح لهذه القناة",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mime = when (channel.type) {
            "hls", "m3u8" -> MimeTypes.APPLICATION_M3U8
            "dash", "mpd" -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        val item = MediaItem.Builder()
            .setUri(url)
            .apply {
                if (mime != null) setMimeType(mime)
            }
            .build()

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

        val dataFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaFactory = DefaultMediaSourceFactory(dataFactory)

        player.setMediaSource(mediaFactory.createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            playerView.isControllerFullyVisible
        ) {
            playerView.hideController()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        player.release()
        executor.shutdownNow()
        super.onDestroy()
    }
}
