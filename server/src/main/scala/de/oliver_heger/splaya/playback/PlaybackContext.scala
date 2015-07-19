/*
 * Copyright 2015 The Developers Team.
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

package de.oliver_heger.splaya.playback

import java.io.InputStream
import javax.sound.sampled.{AudioFormat, SourceDataLine}

/**
 * Companion object for ''PlaybackContext''.
 */
object PlaybackContext {
  /** Constant for the default audio buffer size. */
  val DefaultAudioBufferSize = 4096

  /**
   * Calculates the correct buffer size for the specified audio format.
   * @param format the audio format
   * @return the buffer size for this audio format
   */
  private def calculateBufferSize(format: AudioFormat): Int =
    if (DefaultAudioBufferSize % format.getFrameSize != 0)
      ((DefaultAudioBufferSize / format.getFrameSize) + 1) * format.getFrameSize
    else DefaultAudioBufferSize
}

/**
 * A trait allowing access to all information required during audio playback.
 *
 * When playing audio data a couple of objects is involved, e.g. an audio
 * stream, a data line, a format object. This trait defines these properties.
 */
trait PlaybackContext {

  import de.oliver_heger.splaya.playback.PlaybackContext._

  /** The format of the represented audio data. */
  val format: AudioFormat

  /**
   * The stream for reading audio data. Typically, this is a special audio
   * stream.
   */
  val stream: InputStream

  /**
   * The line used for playback.
   */
  val line: SourceDataLine

  /**
   * The size of an audio buffer to be used for this playback context.
   * There may be restrictions regarding the buffer size in respect of the
   * current audio format. This property returns a valid buffer size.
   */
  lazy val bufferSize: Int = calculateBufferSize(format)
}
