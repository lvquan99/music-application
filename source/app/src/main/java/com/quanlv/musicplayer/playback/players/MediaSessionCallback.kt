/*
 * Copyright (c) 2020. Carlos René Ramos López. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quanlv.musicplayer.playback.players

import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import androidx.core.os.bundleOf
import com.quanlv.musicplayer.extensions.isPlaying
import com.quanlv.musicplayer.extensions.toIdList
import com.quanlv.musicplayer.extensions.toMediaId
import com.quanlv.musicplayer.playback.AudioFocusHelper
import com.quanlv.musicplayer.repository.SongsRepository
import com.quanlv.musicplayer.utils.BeatConstants.BY_UI_KEY
import com.quanlv.musicplayer.utils.BeatConstants.PAUSE_ACTION
import com.quanlv.musicplayer.utils.BeatConstants.PLAY_ACTION
import com.quanlv.musicplayer.utils.BeatConstants.PLAY_ALL_SHUFFLED
import com.quanlv.musicplayer.utils.BeatConstants.QUEUE_LIST_TYPE_KEY
import com.quanlv.musicplayer.utils.BeatConstants.REMOVE_SONG
import com.quanlv.musicplayer.utils.BeatConstants.REPEAT_ALL
import com.quanlv.musicplayer.utils.BeatConstants.REPEAT_MODE
import com.quanlv.musicplayer.utils.BeatConstants.REPEAT_ONE
import com.quanlv.musicplayer.utils.BeatConstants.SEEK_TO
import com.quanlv.musicplayer.utils.BeatConstants.SET_MEDIA_STATE
import com.quanlv.musicplayer.utils.BeatConstants.SHUFFLE_MODE
import com.quanlv.musicplayer.utils.BeatConstants.SONG_KEY
import com.quanlv.musicplayer.utils.BeatConstants.SONG_LIST_NAME
import com.quanlv.musicplayer.utils.BeatConstants.UPDATE_QUEUE
import com.quanlv.musicplayer.utils.SettingsUtility.Companion.QUEUE_INFO_KEY
import com.quanlv.musicplayer.utils.SettingsUtility.Companion.QUEUE_LIST_KEY
import timber.log.Timber

class MediaSessionCallback(
    private val mediaSession: MediaSessionCompat,
    private val musicPlayer: BeatPlayer,
	private val audioFocusHelper: AudioFocusHelper,
    private val songsRepository: SongsRepository
) : MediaSessionCompat.Callback() {

	init {
        audioFocusHelper.onAudioFocusGain {
            Timber.d("GAIN")
            if (isAudioFocusGranted && !musicPlayer.getSession().isPlaying()) {
                musicPlayer.playSong()
            } else audioFocusHelper.setVolume(AudioManager.ADJUST_RAISE)
            isAudioFocusGranted = false
        }
        audioFocusHelper.onAudioFocusLoss {
            Timber.d("LOSS")
            abandonPlayback()
            isAudioFocusGranted = false
            musicPlayer.pause()
        }

        audioFocusHelper.onAudioFocusLossTransient {
            Timber.d("TRANSIENT")
            if (musicPlayer.getSession().isPlaying()) {
                isAudioFocusGranted = true
                musicPlayer.pause()
            }
        }

        audioFocusHelper.onAudioFocusLossTransientCanDuck {
            Timber.d("TRANSIENT_CAN_DUCK")
            audioFocusHelper.setVolume(AudioManager.ADJUST_LOWER)
        }
    }

    override fun onPause() {
        Timber.d("onPause()")
        musicPlayer.pause()
    }

    override fun onPlay() {
        Timber.d("onPlay()")
        playOnFocus()
    }

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        Timber.d("onPlayFromSearch()")
        query?.let {
            val song = songsRepository.search(query, 1)
            if (song.isNotEmpty()) {
                musicPlayer.playSong(song.first())
            }
        } ?: onPlay()
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        Timber.d("onPlayFromMediaId()")

        val songId = mediaId.toMediaId().mediaId!!.toLong()
        val queue = extras?.getLongArray(QUEUE_INFO_KEY)
        val queueTitle = extras?.getString(SONG_LIST_NAME)
        val seekTo = extras?.getInt(SEEK_TO) ?: 0

        if (queue != null) {
            musicPlayer.setData(queue, queueTitle!!)
        }

        if (seekTo > 0) {
            musicPlayer.seekTo(seekTo)
        }

        musicPlayer.playSong(songId)
    }

    override fun onSeekTo(pos: Long) {
        Timber.d("onSeekTo()")
        musicPlayer.seekTo(pos.toInt())
    }

    override fun onSkipToNext() {
        Timber.d("onSkipToNext()")
        musicPlayer.nextSong()
    }

    override fun onSkipToPrevious() {
        Timber.d("onSkipToPrevious()")
        musicPlayer.previousSong()
    }

    override fun onStop() {
        Timber.d("onStop()")
        musicPlayer.stop()
    }

    override fun onSetRepeatMode(repeatMode: Int) {
        super.onSetRepeatMode(repeatMode)
        val bundle = mediaSession.controller.playbackState.extras ?: Bundle()
        musicPlayer.setPlaybackState(
            Builder(mediaSession.controller.playbackState)
                .setExtras(bundle.apply {
                    putInt(REPEAT_MODE, repeatMode)
                }).build()
        )
    }

    override fun onSetShuffleMode(shuffleMode: Int) {
        super.onSetShuffleMode(shuffleMode)
        val bundle = mediaSession.controller.playbackState.extras ?: Bundle()
        musicPlayer.setPlaybackState(
            Builder(mediaSession.controller.playbackState)
                .setExtras(bundle.apply {
                    putInt(SHUFFLE_MODE, shuffleMode)
                }).build()
        )
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        when (action) {
            SET_MEDIA_STATE -> setSavedMediaSessionState()
            REPEAT_ONE -> musicPlayer.repeatSong()
            REPEAT_ALL -> musicPlayer.repeatQueue()
            REMOVE_SONG -> musicPlayer.removeFromQueue(extras?.getLong(SONG_KEY)!!)
            PAUSE_ACTION -> musicPlayer.pause(extras ?: bundleOf(BY_UI_KEY to true))
            PLAY_ACTION -> playOnFocus(extras ?: bundleOf(BY_UI_KEY to true))

            UPDATE_QUEUE -> {
                extras ?: return

                val queue = extras.getLongArray(QUEUE_LIST_KEY) ?: longArrayOf()
                val queueTitle = extras.getString(QUEUE_LIST_TYPE_KEY) ?: ""

                musicPlayer.updateData(queue, queueTitle)
            }

            PLAY_ALL_SHUFFLED -> {
                extras ?: return

                val controller = mediaSession.controller ?: return

                val queue = extras.getLongArray(QUEUE_INFO_KEY) ?: longArrayOf()
                val queueTitle = extras.getString(SONG_LIST_NAME) ?: ""

                musicPlayer.setData(queue, queueTitle)

                controller.transportControls.setShuffleMode(SHUFFLE_MODE_ALL)

                musicPlayer.nextSong()
            }
        }
    }

    private fun setSavedMediaSessionState() {
        val controller = mediaSession.controller ?: return
        if (controller.playbackState == null || controller.playbackState.state == STATE_NONE) {
            musicPlayer.restoreQueueData()
        } else {
            restoreMediaSession()
        }
    }

    private fun restoreMediaSession() {
        mediaSession.setMetadata(mediaSession.controller.metadata)
        musicPlayer.setPlaybackState(mediaSession.controller.playbackState)
        musicPlayer.setData(
            mediaSession.controller.queue.toIdList(),
            mediaSession.controller.queueTitle.toString()
        )
    }

	private fun playOnFocus(extras: Bundle = bundleOf(BY_UI_KEY to true)) {
        if (audioFocusHelper.requestPlayback())
            musicPlayer.playSong(extras)
    }
}