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

package de.oliver_heger.linedj.player.engine.impl

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._

import de.oliver_heger.linedj.player.engine.PlayerConfig

object BufferFileManager {
  /** The system property pointing to the user's home directory. */
  private val PropUserHome = "user.home"

  /**
    * Creates a new instance of ''BufferFileManager'' based on the specified
    * ''PlayerConfig'' object. The options related to the buffer manager are
    * evaluated. Especially, the temporary directory is determined and
    * created if necessary.
    *
    * @param config the ''PlayerConfig''
    * @return the newly created ''BufferFileManager'' instance
    */
  def apply(config: PlayerConfig): BufferFileManager = {
    val path: Path = determineTempDirectory(config)
    new BufferFileManager(Files createDirectories path, config.bufferFilePrefix, config
      .bufferFileExtension)
  }

  /**
    * Determines the path to the temporary directory based on the specified
    * player configuration. This method evaluates the ''bufferTempPath'' and
    * ''bufferTempPathParts'' configuration options. If a path is provided, it
    * is used. Otherwise, a subdirectory of the user's home directory is used
    * as temporary buffer path.
    *
    * @param config the ''PlayerConfig''
    * @return the temporary directory for the buffer
    */
  private def determineTempDirectory(config: PlayerConfig): Path =
    config.bufferTempPath match {
      case Some(p) => p
      case None =>
        val home = Paths get System.getProperty(PropUserHome)
        config.bufferTempPathParts.foldLeft(home)((p, d) => p resolve d)
    }
}

/**
  * A helper class for managing temporary files for [[LocalBufferActor]].
 *
 * The data streamed into and from the buffer is kept in temporary files. These
 * files are created (up to a number of 2) when data comes in. They are then
 * exposed via a ''FileReaderActor'', and finally removed when they have been
 * read.
 *
 * This class takes care of the creation and management of temporary files. It
 * supports the creation of temporary files and their removal when they have
 * been processed. In addition, it provides a functionality for removing all
 * temporary files which are currently managed or which can be found in the
 * managed directory. An instance can be configured with the directory in
 * which temporary files are created. A prefix and suffix for temporary files
 * can be specified as well.
 *
 * @param directory the directory in which to create temporary files
 * @param prefix the prefix for temporary files
 * @param extension the file extension for temporary files
 */
class BufferFileManager(val directory: Path, val prefix: String, val extension: String) {
  /** An array for storing the files contained in this buffer. */
  private val content = Array[Option[Path]](None, None)

  /** A counter for generating file names. */
  private var counter = 0

  def createPath(): Path = {
    val name = s"$prefix$counter$extension"
    counter += 1
    directory resolve name
  }

  /**
   * Returns a flag whether this buffer is already full. This is case if it
   * contains two files. Then no more files can be appended.
   *
   * @return a flag whether this buffer is full
   */
  def isFull: Boolean = content(1).isDefined

  /**
   * Safely removes the specified path from disk. If the path does not exist,
   * this method has no effect. Note that this method does not change the
   * content of this buffer; only the specified file is deleted, no matter
   * whether it is contained in this buffer or not.
   *
   * @param path the path to be removed
   * @return the path that was passed in
   */
  def removePath(path: Path): Path = {
    Files deleteIfExists path
    path
  }

  /**
   * Returns an option for the next file to be read from the buffer.
   * If there is no such file, result is ''None''.
   *
   * @return an option for the next file to be read
   */
  def read: Option[Path] = content(0)

  /**
   * Appends the specified path to this buffer. It is stored as the last
   * element.
   *
   * @param path the path to be appended
   */
  def append(path: Path): Unit = {
    if (isFull) {
      throw new IllegalStateException("Cannot append to a full buffer!")
    }

    val index = if (content(0).isEmpty) 0 else 1
    content(index) = Some(path)
  }

  /**
   * Removes the first path from this buffer and returns it. If the buffer is
   * empty, an exception is thrown.
   *
   * @return the first path in this buffer which has been removed
   */
  def checkOut(): Path = {
    val path = content(0).get
    content(0) = content(1)
    content(1) = None
    path
  }

  /**
   * Checks out the first path from this buffer and removes it from disk. This
   * is a combination of the methods ''checkOut()'' and ''removePath()''. If
   * the buffer is empty, an exception is thrown.
   *
   * @return the path which has been removed
   */
  def checkOutAndRemove(): Path = removePath(checkOut())

  /**
   * Removes the paths that are currently stored in this buffer. This is
   * useful for instance when the application is shut down; then some cleanup
   * has to be done.
   *
   * @return a sequence with the paths which were contained in this buffer
   */
  def removeContainedPaths(): Seq[Path] = {
    val definedPaths = content.flatten
    definedPaths foreach removePath
    definedPaths
  }

  /**
   * Removes all files in the managed directory matching the configured prefix
   * and extension. This method can be used to do clean up, e.g. at startup or
   * closing time.
   */
  def clearBufferDirectory(): Unit = {
    Files.walkFileTree(directory, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val name = file.getFileName.toString
        if (name.startsWith(prefix) && name.endsWith(extension)) {
          removePath(file)
        }
        FileVisitResult.CONTINUE
      }
    })
  }
}
