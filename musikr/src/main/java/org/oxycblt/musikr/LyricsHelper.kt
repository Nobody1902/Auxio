/*
 * Copyright (c) 2024 Auxio Project
 * LyricsHelper.kt is part of Auxio.
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

package org.oxycblt.musikr

import android.content.Context
import org.oxycblt.musikr.fs.AddedMs
import org.oxycblt.musikr.fs.File
import org.oxycblt.musikr.metadata.MetadataExtractor
import org.oxycblt.musikr.metadata.MetadataResult
import org.oxycblt.musikr.tag.parse.lyrics

/**
 * Helper function to extract lyrics from a Song.
 * This is a public API wrapper around the internal metadata extraction.
 *
 * @param context Android context
 * @param song The song to extract lyrics from
 * @return The raw lyrics text, or null if no lyrics found
 */
suspend fun getLyricsFromSong(context: Context, song: Song): String? {
    val deviceFile = File(
        uri = song.uri,
        path = song.path,
        addedMs = object : AddedMs {
            override suspend fun resolve(): Long? = null
        },
        modifiedMs = 0,
        mimeType = "",
        size = 0,
        parent = null,
    )
    return when (val result = MetadataExtractor.from(context).extract(deviceFile)) {
        is MetadataResult.Success -> {
            result.metadata?.lyrics()
        }
        else -> null
    }
}
