/*
 * Copyright 2015-2016 The Developers Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package de.oliver_heger.linedj.config

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

object ServerConfigSpec {
  /** The reader check interval. */
  private val ReaderCheckInterval = 10.minutes

  /** The reader check delay. */
  private val ReaderCheckDelay = 5.minutes

  /** Test root object. */
  private val MusicRoot = ServerConfig.MediaRootData(Paths get "music1", 3, None)

  /** Another root object. */
  private val CDRomRoot = ServerConfig.MediaRootData(Paths get "cdrom", 1, Some(1))

  /** Test value for the chunk size when extracting meta data. */
  private val ReadChunkSize = 16384

  /** Test value for the size limit for ID3 tags. */
  private val TagSizeLimit = 4096

  /** Test value for the chunk size of a meta data notification. */
  private val MetaDataChunkSize = 10

  /** Test value for the maximum message size of meta data chunk messages. */
  private val MetaDataMaxMsgSize = 150

  /** Test value for the meta data persistence path. */
  private val MetaDataPersistencePath = Paths get "persistence"

  /** Test value for the meta data persistence chunk size. */
  private val MetaDataPersistenceChunkSize = 1024

  /** Test value for the meta data persistence parallel count. */
  private val MetaDataPersistenceParallelCount = 3

  /** Test value for the meta data persistence write block size. */
  private val MetaDataPersistenceWriteBlockSize = 40
}

/**
 * Test class for ''ServerConfig''.
 */
class ServerConfigSpec(testSystem: ActorSystem) extends TestKit(testSystem) with FlatSpecLike
with Matchers with BeforeAndAfterAll {

  import ServerConfigSpec._

  def this() = this(ActorSystem("ServerConfigSpec",
    ConfigFactory.parseString(
      s"""splaya {
         | media {
         |   readerTimeout = 60s
         |   readerCheckInterval = ${ServerConfigSpec.ReaderCheckInterval.toString()}
          |   readerCheckInitialDelay = ${ServerConfigSpec.ReaderCheckDelay.toString()}
          |   paths = [
          |     {
          |       path = ${ServerConfigSpec.MusicRoot.rootPath}
          |       processorCount = ${ServerConfigSpec.MusicRoot.processorCount}
          |     },
          |     {
          |       path = ${ServerConfigSpec.CDRomRoot.rootPath}
          |       processorCount = ${ServerConfigSpec.CDRomRoot.processorCount}
          |       accessRestriction = ${ServerConfigSpec.CDRomRoot.accessRestriction.get}
          |     }
          |   ]
          |   excludedExtensions = [
          |       "JPG", "pdf", "tex"
          |   ]
          |   metaDataExtraction {
          |     readChunkSize = ${ServerConfigSpec.ReadChunkSize}
          |     tagSizeLimit = ${ServerConfigSpec.TagSizeLimit}
          |     metaDataUpdateChunkSize = ${ServerConfigSpec.MetaDataChunkSize}
          |     metaDataMaxMessageSize = ${ServerConfigSpec.MetaDataMaxMsgSize}
          |   }
          |   metaDataPersistence {
          |     path = ${ServerConfigSpec.MetaDataPersistencePath}
          |     chunkSize = ${ServerConfigSpec.MetaDataPersistenceChunkSize}
          |     parallelCount = ${ServerConfigSpec.MetaDataPersistenceParallelCount}
          |     writeBlockSize = ${ServerConfigSpec.MetaDataPersistenceWriteBlockSize}
          |   }
          | }
          |}
       """.stripMargin)))

  override protected def afterAll(): Unit = {
    TestKit shutdownActorSystem system
  }

  /**
   * Convenience method for creating a test configuration object based on the
   * config of the test actor system.
    *
    * @return the test configuration
   */
  private def createConfig(): ServerConfig = ServerConfig(system.settings.config)

  "A ServerConfig" should "return the timeout for reader actors" in {
    createConfig().readerTimeout should be(60.seconds)
  }

  it should "return the reader check interval" in {
    createConfig().readerCheckInterval should be(ReaderCheckInterval)
  }

  it should "return the initial reader check delay" in {
    createConfig().readerCheckInitialDelay should be(ReaderCheckDelay)
  }

  it should "return a collection of paths with media files" in {
    val paths = createConfig().mediaRoots
    paths should contain only(MusicRoot, CDRomRoot)
  }

  it should "allow access to a media root based on its path" in {
    val config = createConfig()

    config rootFor MusicRoot.rootPath should be(Some(MusicRoot))
    config rootFor CDRomRoot.rootPath should be(Some(CDRomRoot))
  }

  it should "return None for an unknown root path" in {
    createConfig() rootFor Paths.get("unknownPath") shouldBe 'empty
  }

  it should "return the read chunk size" in {
    createConfig().metaDataReadChunkSize should be(ReadChunkSize)
  }

  it should "return the tag size limit" in {
    createConfig().tagSizeLimit should be(TagSizeLimit)
  }

  it should "return the meta data chunk size" in {
    createConfig().metaDataUpdateChunkSize should be(MetaDataChunkSize)
  }

  it should "return the maximum meta data message size" in {
    createConfig().metaDataMaxMessageSize should be(MetaDataMaxMsgSize)
  }

  it should "correct the maximum message size if necessary" in {
    val oc = createConfig()
    val config = new ServerConfig(readerTimeout = oc.readerTimeout, readerCheckInterval = oc
      .readerCheckInterval,
      readerCheckInitialDelay = oc.readerCheckInitialDelay, metaDataReadChunkSize = oc
        .metaDataReadChunkSize,
      tagSizeLimit = oc.tagSizeLimit, excludedFileExtensions = oc.excludedFileExtensions,
      rootMap = Map.empty, metaDataUpdateChunkSize = 8, initMetaDataMaxMsgSize = 150,
      metaDataPersistencePath = Paths get "foo", metaDataPersistenceChunkSize = 42,
      metaDataPersistenceParallelCount = 2, metaDataPersistenceWriteBlockSize = 43)

    config.metaDataMaxMessageSize should be(152)
  }

  it should "return the file extensions to be excluded" in {
    createConfig().excludedFileExtensions should contain only("JPG", "TEX", "PDF")
  }

  it should "return the path for meta data persistence" in {
    createConfig().metaDataPersistencePath should be(MetaDataPersistencePath)
  }

  it should "return the meta data persistence chunk size" in {
    createConfig().metaDataPersistenceChunkSize should be(MetaDataPersistenceChunkSize)
  }

  it should "return the meta data persistence parallel count" in {
    createConfig().metaDataPersistenceParallelCount should be(MetaDataPersistenceParallelCount)
  }

  it should "return the meta data persistence write block size" in {
    createConfig().metaDataPersistenceWriteBlockSize should be(MetaDataPersistenceWriteBlockSize)
  }
}
