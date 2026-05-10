/*
 * Copyright (c) 2024 Auxio Project
 * LrcParser.kt is part of Auxio.
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

import java.util.regex.Pattern

/**
 * Parser for LRC (Lyric) format files.
 * Supports timestamps like [mm:ss.xx] or [mm:ss.xxx]
 *
 * @author Auxio Team
 */
object LrcParser {
    private val TIMESTAMP_REGEX = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")
    private val LINE_REGEX = Pattern.compile("""(\[\d{2}:\d{2}\.\d{2,3}\])+(.+)""")

    /**
     * Parse LRC format lyrics into a list of [LyricLine] with timestamps.
     *
     * @param lrcText The raw LRC text
     * @return List of [LyricLine] sorted by time, or null if no timestamps found
     */
    fun parse(lrcText: String): List<LyricLine>? {
        val lines = mutableListOf<LyricLine>()
        val timestampPattern = TIMESTAMP_REGEX

        val lines_text = lrcText.lines()
        for (line in lines_text) {
            val matcher = timestampPattern.matcher(line)
            val timestamps = mutableListOf<Long>()
            
            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: continue
                val sec = matcher.group(2)?.toLongOrNull() ?: continue
                val frac = matcher.group(3) ?: continue
                
                // Convert to milliseconds
                val ms = min * 60 * 1000 + sec * 1000 + frac.padEnd(3, '0').substring(0, 3).toLong()
                timestamps.add(ms)
            }
            
            if (timestamps.isNotEmpty()) {
                val lyricText = line.replace(Regex("""\[\d{2}:\d{2}\.\d{2,3}\]"""), "").trim()
                for (timestamp in timestamps) {
                    lines.add(LyricLine(timestamp, lyricText))
                }
            }
        }
        
        if (lines.isEmpty()) {
            return null
        }
        
        return lines.sortedBy { it.timeMs }
    }

    /**
     * Check if the text contains LRC timestamps.
     */
    fun hasTimestamps(text: String): Boolean {
        return TIMESTAMP_REGEX.matcher(text).find()
    }
}

/**
 * Represents a single line of lyrics with its timestamp.
 *
 * @property timeMs The timestamp in milliseconds
 * @property text The lyric text
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)
