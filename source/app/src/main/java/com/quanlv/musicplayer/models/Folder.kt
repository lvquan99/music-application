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

package com.quanlv.musicplayer.models

import android.content.Context
import com.quanlv.musicplayer.extensions.fixedName
import com.quanlv.musicplayer.extensions.fixedPath
import com.quanlv.musicplayer.utils.BeatConstants.FOLDER_TYPE
import com.google.gson.Gson
import java.io.File

class Folder(
    val id: Long = -1,
    val name: String = "",
    val albumId: Long = -1,
    val path: String = "",
    val ids: LongArray = longArrayOf()
) : MediaItem(id) {

    companion object {
        fun fromSong(song: Song, songs: LongArray, context: Context): Folder {
            return Folder(
                song.id,
                File(song.path).fixedName(context),
                song.albumId,
                File(song.path).fixedPath(context),
                songs
            )
        }
    }

    override fun compare(other: MediaItem): Boolean {
        other as Folder
        return id == other.id && name == other.name && albumId == other.albumId
    }

    override fun toString(): String {
        return Gson().toJson(this)
    }

    fun toFavorite(): Favorite {
        return Favorite(id, name, path, albumId, 0, ids.size, FOLDER_TYPE)
    }
}