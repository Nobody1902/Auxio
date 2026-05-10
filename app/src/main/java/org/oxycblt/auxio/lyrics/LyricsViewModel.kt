/*
 * Copyright (c) 2024 Auxio Project
 * LyricsViewModel.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.lyrics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.getLyricsFromSong
import timber.log.Timber as L

/**
 * ViewModel that manages lyrics display state.
 *
 * @author Auxio Team
 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val playbackManager: PlaybackStateManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _lyricsLines = MutableStateFlow<List<LyricLine>?>(null)
    val lyricsLines: StateFlow<List<LyricLine>?> = _lyricsLines

    private val _currentLineIndex = MutableStateFlow(-1)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex

    private val _hasTimestamps = MutableStateFlow(false)
    val hasTimestamps: StateFlow<Boolean> = _hasTimestamps

    private val _followEnabled = MutableStateFlow(true)
    val followEnabled: StateFlow<Boolean> = _followEnabled

    fun toggleFollow() {
        _followEnabled.value = !_followEnabled.value
    }

    fun onSongChanged(song: Song?) {
        loadLyrics(song)
    }

    fun updatePlaybackPosition(positionMs: Long) {
        val lines = _lyricsLines.value ?: return
        if (lines.isEmpty()) return

        val hasTimestamps = _hasTimestamps.value
        if (!hasTimestamps) return

        var newIndex = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) {
                newIndex = i
            } else {
                break
            }
        }

        if (newIndex != _currentLineIndex.value) {
            _currentLineIndex.value = newIndex
        }
    }

    fun seekToLine(index: Int) {
        val lines = _lyricsLines.value ?: return
        if (index < 0 || index >= lines.size) return
        val line = lines[index]
        if (line.timeMs >= 0) {
            L.d("Lyrics: Seeking to line $index at ${line.timeMs}ms")
            playbackManager.seekTo(line.timeMs)
        }
    }

    private fun loadLyrics(song: Song?) {
        if (song == null) {
            _lyricsLines.value = null
            _currentLineIndex.value = -1
            _hasTimestamps.value = false
            return
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    loadBestLyrics(song)
                }
                if (result != null) {
                    _lyricsLines.value = result
                    _hasTimestamps.value = result.any { it.timeMs >= 0 }
                    L.d("Lyrics: Loaded ${result.size} lines")
                } else {
                    _lyricsLines.value = null
                    _hasTimestamps.value = false
                    L.d("Lyrics: No lyrics found for ${song.name}")
                }
            } catch (e: Exception) {
                L.e(e, "Lyrics: Failed to load lyrics for ${song.name}")
                _lyricsLines.value = null
                _hasTimestamps.value = false
            }
        }
    }

    /**
     * Load lyrics in priority order:
     * 1. Embedded synchronized lyrics (from metadata tags)
     * 2. Sidecar .lrc file (alongside the audio file)
     * 3. Embedded unsynchronized lyrics (plain text)
     */
    private suspend fun loadBestLyrics(song: Song): List<LyricLine>? {
        // Priority 1: Embedded synchronized lyrics
        val embeddedText = getLyricsFromSong(context, song)
        if (embeddedText != null) {
            val parsed = LrcParser.parse(embeddedText)
            if (parsed != null) {
                L.d("Lyrics: Using embedded synchronized lyrics")
                return parsed
            }
            // Has text but no timestamps — keep as fallback
            L.d("Lyrics: Embedded lyrics have no timestamps, checking sidecar")
        }

        // Priority 2: Sidecar .lrc file
        val sidecarText = readSidecarLrc(song)
        if (sidecarText != null) {
            val parsed = LrcParser.parse(sidecarText)
            if (parsed != null) {
                L.d("Lyrics: Using sidecar .lrc file")
                return parsed
            }
            // .lrc has no timestamps — use as static lyrics
            L.d("Lyrics: Sidecar .lrc has no timestamps")
            return textToUntimedLines(sidecarText)
        }

        // Priority 3: Embedded unsynchronized (plain text)
        if (embeddedText != null) {
            L.d("Lyrics: Using embedded unsynchronized lyrics")
            return textToUntimedLines(embeddedText)
        }

        return null
    }

    private suspend fun readSidecarLrc(song: Song): String? = withContext(Dispatchers.IO) {
        try {
            val resolvedPath = resolveSongPath(song) ?: return@withContext null
            val audioFile = File(resolvedPath)
            val parentDir = audioFile.parentFile ?: return@withContext null
            val baseName = audioFile.nameWithoutExtension
            val lrcFile = File(parentDir, "$baseName.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                L.d("Lyrics: Found sidecar .lrc at ${lrcFile.absolutePath}")
                return@withContext lrcFile.readText()
            }
            null
        } catch (e: Exception) {
            L.e(e, "Lyrics: Failed to read sidecar .lrc")
            null
        }
    }

    private suspend fun resolveSongPath(song: Song): String? = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(song.uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val dataIndex = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                    if (dataIndex >= 0) {
                        return@withContext c.getString(dataIndex)
                    }
                }
            }
            null
        } catch (e: Exception) {
            L.e(e, "Lyrics: Failed to resolve song path")
            null
        }
    }

    private fun textToUntimedLines(text: String): List<LyricLine> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricLine(-1, it) }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
