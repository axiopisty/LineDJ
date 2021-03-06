/*
 * Copyright 2015-2016 The Developers Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.oliver_heger.linedj.player.engine

import de.oliver_heger.linedj.media.MediumID

object AudioSource {
  /**
    * Constant for a length indicating an infinite source. This is used for
    * instance for internet radio streams.
    */
  val InfiniteLength = Long.MaxValue

  /**
    * Creates a source with an infinite length for the specified URI. For such
    * sources skip information is irrelevant.
    *
    * @param uri the URI of the source
    * @return the new ''AudioSource''
    */
  def infinite(uri: String): AudioSource = apply(uri, InfiniteLength, 0, 0)
}

/**
 * A data class describing an audio source to be played by the audio player
 * engine. This class contains some meta data about the source and its position
 * in the current playlist. It is mainly used internally by the engine.
 *
 * @param uri the URI of the source
 * @param length the length of the source (in bytes)
 * @param skip the skip position (i.e. the part of the stream at the beginning
 *             which is to be ignored; actual playback starts after this position)
 * @param skipTime the skip time
 */
case class AudioSource(uri: String, length: Long, skip: Long, skipTime: Long) {
  /**
    * Checks whether this is a source with an infinite length.
    *
    * @return '''true''' if the length is infinite, '''false''' otherwise
    */
  def isInfinite: Boolean = length == AudioSource.InfiniteLength
}

/**
 * A data class which uniquely identifies an audio source.
 *
 * This message class is used to obtain information about a specific audio
 * source file (its content plus additional media data) from the actor managing
 * the currently available sources. A source is uniquely identified using the
 * medium ID and the (relative) URI within this medium.
 *
 * @param mediumID the ID of the medium the desired source belongs to
 * @param uri the URI of the desired source relative to the medium
 */
case class AudioSourceID(mediumID: MediumID, uri: String)

/**
 * A data class representing an entry in a playlist.
 *
 * An audio player client maintains a list with objects of this class defining
 * the audio files to be played. The information contained here is sufficient
 * to request actual audio content from a media manager and initiate playback.
 *
 * @param sourceID the ID of the audio source in question
 * @param skip the skip position
 * @param skipTime the skip time
 */
case class AudioSourcePlaylistInfo(sourceID: AudioSourceID, skip: Long, skipTime: Long)
