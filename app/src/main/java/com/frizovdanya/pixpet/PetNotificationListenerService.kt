package com.frizovdanya.pixpet

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

/**
 * Optional, user-authorized integration. Notification text is inspected in memory only to choose
 * a mood; PixPet never stores or transmits message contents.
 */
class PetNotificationListenerService : NotificationListenerService() {
    private var mediaManager: MediaSessionManager? = null
    private var mediaListenerRegistered = false
    private var lastEventKey: String? = null
    private var lastEventAt = 0L

    private val mediaListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        publishMediaState(isTelegramMediaPlaying(controllers))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val manager = getSystemService(MediaSessionManager::class.java) ?: return
            mediaManager = manager
            val component = ComponentName(this, PetNotificationListenerService::class.java)
            manager.addOnActiveSessionsChangedListener(mediaListener, component, Handler(Looper.getMainLooper()))
            mediaListenerRegistered = true
            publishMediaState(isTelegramMediaPlaying(manager.getActiveSessions(component)))
        } catch (_: SecurityException) {
            mediaListenerRegistered = false
        } catch (_: Exception) {
            mediaListenerRegistered = false
        }
    }

    override fun onListenerDisconnected() {
        unregisterMediaListener()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        unregisterMediaListener()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!PetPrefs.load(this).reactNotifications) return
        if (sbn.packageName !in TELEGRAM_PACKAGES) return
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val text = buildString(400) {
            append(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
        }.lowercase().take(400)
        if (text.isBlank()) return

        val mood = when {
            LOVE.containsMatchIn(text) -> "love"
            LAUGH.containsMatchIn(text) -> "laugh"
            HELLO.containsMatchIn(text) -> "hi"
            else -> "mail"
        }
        // Ignore repeated updates to the same status bar item for a short moment.
        val key = "${sbn.packageName}:${sbn.id}:${sbn.tag.orEmpty()}"
        val now = SystemClock.elapsedRealtime()
        if (key == lastEventKey && now - lastEventAt < 1_200L) return
        lastEventKey = key
        lastEventAt = now
        sendBroadcast(
            Intent(PixPetContract.ACTION_REACTION)
                .setPackage(packageName)
                .putExtra(PixPetContract.EXTRA_MOOD, mood),
        )
    }

    private fun isTelegramMediaPlaying(controllers: List<MediaController>?): Boolean {
        if (controllers.isNullOrEmpty()) return false
        return controllers.any { controller ->
            controller.packageName in TELEGRAM_PACKAGES &&
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
        }
    }

    private fun publishMediaState(playing: Boolean) {
        if (!PetPrefs.load(this).dance) return
        sendBroadcast(
            Intent(PixPetContract.ACTION_MEDIA)
                .setPackage(packageName)
                .putExtra(PixPetContract.EXTRA_PLAYING, playing),
        )
    }

    private fun unregisterMediaListener() {
        if (!mediaListenerRegistered) return
        try { mediaManager?.removeOnActiveSessionsChangedListener(mediaListener) } catch (_: Exception) { }
        mediaManager = null
        mediaListenerRegistered = false
    }

    companion object {
        private val TELEGRAM_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "org.thunderdog.challegram",
        )
        private val LOVE = Regex("❤|♥|💕|💖|😘|😍|🥰|\\bлюблю\\b|\\blove\\b")
        private val LAUGH = Regex("(?:ха){2,}|(?:хе){2,}|(?:хи){2,}|\\bлол\\b|\\blol\\b|😂|🤣")
        private val HELLO = Regex("\\b(?:привет\\w*|здравствуй\\w*|хай|салют|hello|hi|hey)\\b")
    }
}
