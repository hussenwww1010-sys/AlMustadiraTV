
package com.almustadira.tv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelList: LinearLayout
    private var selectedUrl: String? = null

    private val backgroundColor = Color.rgb(6, 9, 24)
    private val cyanColor = Color.rgb(0, 229, 255)
    private val purpleColor = Color.rgb(168, 85, 247)

    data class Channel(
        val name: String,
        val url: String,
        val type: String = "hls"
    )

    private val channels = listOf(
        Channel(
            "قناة تجريبية",
            "",
            "hls"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        player = ExoPlayer.Builder(this).build()

        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }

        buildInterface()
        showChannels()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@MainActivity,
                    "تعذر تشغيل القناة، تحقق من رابط البث",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(16, 12, 16, 12)
        }

        val header = TextView(this).apply {
            text = "المستديرة TV"
            textSize = 25f
            setTextColor(cyanColor)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 16)
            isFocusable = true
        }

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        channelList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 12, 0)
        }

        content.addView(
            channelList,
            LinearLayout.LayoutParams(0, 320, 1f)
        )

        content.addView(
            playerView,
            LinearLayout.LayoutParams(0, 320, 2f)
        )

        root.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun showChannels() {
        channelList.removeAllViews()

        channels.forEach { channel ->
            val button = TextView(this).apply {
                text = channel.name
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(17, 10, 44))
                setPadding(10, 18, 10, 18)
                isFocusable = true
                isClickable = true

                setOnClickListener {
                    playChannel(channel)
                }

                setOnFocusChangeListener { view, focused ->
                    view.setBackgroundColor(
                        if (focused) purpleColor
                        else Color.rgb(17, 10, 44)
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
        val url = channel.url.trim()

        if (url.isEmpty() ||
            !(url.startsWith("https://") ||
              url.startsWith("http://"))
        ) {
            Toast.makeText(
                this,
                "أضف رابط بث صحيح للقناة أولاً",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        selectedUrl = url

        val mimeType = when (channel.type.lowercase()) {
            "hls" -> MimeTypes.APPLICATION_M3U8
            "dash" -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        val item = MediaItem.Builder()
            .setUri(url)
            .apply {
                if (mimeType != null) setMimeType(mimeType)
            }
            .build()

        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (player.isCommandAvailable(
                        Player.COMMAND_PLAY_PAUSE
                    )
                ) {
                    if (player.isPlaying) player.pause()
                    else player.play()
                    return true
                }
            }

            KeyEvent.KEYCODE_BACK -> {
                if (playerView.isControllerFullyVisible) {
                    playerView.hideController()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
