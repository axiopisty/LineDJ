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

package de.oliver_heger.linedj.metadata.persistence

import java.nio.file.{Path, Paths}
import java.util.concurrent.{ArrayBlockingQueue, LinkedBlockingQueue, TimeUnit}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import de.oliver_heger.linedj.config.ServerConfig
import de.oliver_heger.linedj.io.FileData
import de.oliver_heger.linedj.media.{EnhancedMediaScanResult, MediaScanResult, MediumID}
import de.oliver_heger.linedj.metadata.persistence.PersistentMetaDataReaderActor.ReadMetaDataFile
import de.oliver_heger.linedj.metadata.persistence.PersistentMetaDataWriterActor.ProcessMedium
import de.oliver_heger.linedj.metadata.persistence.parser.{MetaDataParser, ParserImpl}
import de.oliver_heger.linedj.metadata.{MediaMetaData, MetaDataProcessingResult, UnresolvedMetaDataFiles}
import de.oliver_heger.linedj.utils.ChildActorFactory
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.annotation.tailrec
import scala.reflect.ClassTag

object PersistenceMetaDataManagerActorSpec {
  /** A test path with persistent meta data files. */
  private val FilePath = Paths get "testPath"

  /** A root path for scan results. */
  private val RootPath = Paths get "root"

  /** The number of concurrent reader actors. */
  private val ParallelCount = 3

  /** The chunk size when reading meta data files. */
  private val ChunkSize = 42

  /** The persistent meta data write block size. */
  private val WriteBlockSize = 33

  /** The number of media files in a medium. */
  private val FileCount = 8

  /** Constant for the reader child actor class. */
  private val ClassReaderChildActor = PersistentMetaDataReaderActor(null, null, 0).actorClass()

  /** Constant for the writer child actor class. */
  private val ClassWriterChildActor = classOf[PersistentMetaDataWriterActor]

  /**
    * Generates a checksum based on the given index.
    *
    * @param index the index
    * @return the checksum for this index
    */
  private def checksum(index: Int): String = "check_" + index

  /**
    * Generates a path for the meta data file associated with the given
    * checksum.
    *
    * @param checksum the checksum
    * @return the corresponding meta data path
    */
  private def metaDataFile(checksum: String): Path =
    FilePath.resolve(checksum + ".mdt")

  /**
    * Generates a medium ID based on the given index.
    *
    * @param index the index
    * @return the corresponding medium ID
    */
  private def mediumID(index: Int): MediumID = MediumID("someURI" + index, Some("Path" + index))

  /**
    * Generates a read meta file message for the medium with the specified index.
    *
    * @param index the index
    * @return the message for the reader actor
    */
  private def readerMessage(index: Int): PersistentMetaDataReaderActor.ReadMetaDataFile =
    PersistentMetaDataReaderActor.ReadMetaDataFile(metaDataFile(checksum(index)), mediumID(index))

  /**
    * Generates a map with data about meta data files corresponding to the
    * specified indices. Such a map can be returned by the mock file
    * scanner.
    *
    * @param indices the indices of contained media
    * @return a mapping for meta data files
    */
  private def persistentFileMapping(indices: Int*): Map[String, Path] =
    indices.map { i =>
      val cs = checksum(i)
      (cs, metaDataFile(cs))
    }.toMap

  /**
    * Generates a scan result that contains media derived from the passed in
    * indices.
    *
    * @param indices the indices
    * @return the ''MediaScanResult''
    */
  private def scanResult(indices: Int*): MediaScanResult =
    MediaScanResult(root = RootPath,
      mediaFiles = indices.map(i => (mediumID(i), mediumFiles(mediumID(i)))).toMap
    )

  /**
    * Generates an enhanced media scan result that contains media and their
    * checksums derived from the passed in indices.
    *
    * @param indices the indices
    * @return the ''EnhancedMediaScanResult''
    */
  private def enhancedScanResult(indices: Int*): EnhancedMediaScanResult = {
    val checksumMapping = indices.map(i => (mediumID(i), checksum(i))).toMap
    EnhancedMediaScanResult(scanResult(indices: _*), checksumMapping, Map.empty)
  }

  /**
    * Generates a list of files on a test medium.
    *
    * @param mediumID the medium ID
    * @return the files on this test medium
    */
  private def mediumFiles(mediumID: MediumID): List[FileData] = {
    val mediumPath = RootPath resolve mediumID.mediumURI
    (1 to FileCount).map(i => FileData(mediumPath resolve s"song$i.mp3", i * 1000)).toList
  }

  /**
    * Generates the processing results for the medium with the specified index.
    *
    * @param medIdx the index of the medium
    * @return a list with processing results for this medium
    */
  private def processingResults(medIdx: Int): List[MetaDataProcessingResult] =
    processingResults(mediumID(medIdx))

  /**
    * Generates processing results for the specified medium ID.
    *
    * @param mid the medium ID
    * @return a list with processing results for this medium
    */
  private def processingResults(mid: MediumID): List[MetaDataProcessingResult] =
    mediumFiles(mid) map (f => MetaDataProcessingResult(f.path, mid, f.path.toString,
      MediaMetaData(title = Some("Song " + f.path.toString))))

  /**
    * Expects a message of the specified type for each of the passed in test
    * probes.
    *
    * @param probes the test probes
    * @tparam T the type of the message
    * @return a set with all received messages (the order is typically
    *         unspecified)
    */
  private def expectMessages[T](probes: TestProbe*)(implicit t: ClassTag[T]): Set[T] =
    probes.foldLeft(Set.empty[T])((s, p) => s + p.expectMsgType[T])
}

/**
  * Test class for ''PersistenceMetaDataManagerActor''.
  */
class PersistenceMetaDataManagerActorSpec(testSystem: ActorSystem) extends TestKit(testSystem)
  with ImplicitSender with FlatSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar {

  import PersistenceMetaDataManagerActorSpec._

  def this() = this(ActorSystem("PersistenceMetaDataManagerActorSpec"))

  override protected def afterAll(): Unit = {
    TestKit shutdownActorSystem system
  }

  "A PersistenceMetaDataManagerActor" should "create a default file scanner" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val testRef = TestActorRef[PersistentMetaDataManagerActor](PersistentMetaDataManagerActor
    (helper.config))

    testRef.underlyingActor.fileScanner should not be null
  }

  it should "generate correct creation properties" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val props = PersistentMetaDataManagerActor(helper.config)

    classOf[PersistentMetaDataManagerActor].isAssignableFrom(props.actorClass()) shouldBe true
    classOf[ChildActorFactory].isAssignableFrom(props.actorClass()) shouldBe true
  }

  it should "notify the caller for unknown media immediately" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1, 2).createTestActor()
    val result = enhancedScanResult(3, 4)

    actor ! result
    val unresolvedMsgs = Set(expectMsgType[UnresolvedMetaDataFiles],
      expectMsgType[UnresolvedMetaDataFiles])

    def unresolvedMessage(index: Int): UnresolvedMetaDataFiles = {
      val id = mediumID(index)
      UnresolvedMetaDataFiles(id, result.scanResult.mediaFiles(id), result)
    }

    unresolvedMsgs should contain allOf(unresolvedMessage(3), unresolvedMessage(4))
    helper.expectNoChildReaderActor()
  }

  /**
    * Generates a ''ProcessMedium'' message based on the given parameters.
    *
    * @param index    the index
    * @param resolved the number of resolved songs
    * @param result   the enhanced scan result
    * @return the ''ProcessMedium'' message
    */
  private def processMsg(index: Int, resolved: Int, result: EnhancedMediaScanResult):
  ProcessMedium =
    PersistentMetaDataWriterActor.ProcessMedium(target = FilePath.resolve(checksum(index) + ".mdt"),
      mediumID = mediumID(index), metaDataManager = testActor,
      uriPathMapping = result.fileUriMapping, resolvedSize = resolved)

  it should "pass unknown media to the writer actor" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1, 2).createTestActor()
    val result = enhancedScanResult(3)

    actor ! result
    expectMsgType[UnresolvedMetaDataFiles]
    helper.writerActor.expectMsg(processMsg(3, 0, result))
  }

  it should "create reader actors for known media" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1, 2).createTestActor()

    actor ! enhancedScanResult(1, 2, 3)
    expectMsgType[UnresolvedMetaDataFiles].mediumID should be(mediumID(3))
    val readerActors = helper.expectChildReaderActors(count = 2)
    val readMessages = expectMessages[PersistentMetaDataReaderActor.ReadMetaDataFile](readerActors: _*)
    readMessages should contain allOf(readerMessage(1), readerMessage(2))
  }

  it should "create not more reader actors than configured" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1, 2, 3, 4, 5).createTestActor()

    actor ! enhancedScanResult(1, 2, 3, 4, 5)
    helper.expectChildReaderActors(count = ParallelCount)
    helper.expectNoChildReaderActor()
  }

  it should "handle the (unlikely) case that meta data files are retrieved later" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper(skipFileScan = true)
    val actor = helper.initMediaFiles(1, 2).createTestActor()

    actor ! enhancedScanResult(1)
    actor ! PersistentMetaDataManagerActor.ScanForMetaDataFiles
    helper.expectChildReaderActor().expectMsg(readerMessage(1))
  }

  /**
    * Expects that processing results are sent to the listener actor.
    *
    * @param results the expected results
    */
  private def expectProcessingResults(results: List[MetaDataProcessingResult]): Unit = {
    results foreach (r => expectMsg(r))
  }

  it should "send arriving meta data processing results to the manager actor" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1).createTestActor()
    actor ! enhancedScanResult(1)
    val results = processingResults(1) take (FileCount / 2)

    actor ! PersistentMetaDataReaderActor.ProcessingResults(results)
    expectProcessingResults(results)
  }

  it should "not crash for processing results with an unknown medium ID" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1).createTestActor()

    actor receive PersistentMetaDataReaderActor.ProcessingResults(processingResults(1))
  }

  it should "not crash for an empty chunk of processing data" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1).createTestActor()
    actor ! enhancedScanResult(1)

    actor receive PersistentMetaDataReaderActor.ProcessingResults(List.empty)
  }

  it should "start processing of a new medium when a reader actor terminates" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1, 2, 3, 4).createTestActor()
    actor ! enhancedScanResult(1, 2, 3, 4)
    val readerActor = helper.expectChildReaderActor()
    val request = readerActor.expectMsgType[ReadMetaDataFile]
    val readers = helper.expectChildReaderActors(2)
    val messages = expectMessages[ReadMetaDataFile](readers: _*) + request
    val results = processingResults(request.mediumID)
    actor ! PersistentMetaDataReaderActor.ProcessingResults(results)
    expectProcessingResults(results)

    system stop readerActor.ref
    val nextReader = helper.expectChildReaderActor()
    val msg = nextReader.expectMsgType[PersistentMetaDataReaderActor.ReadMetaDataFile]
    (messages + msg).map(_.mediumID) should contain allOf(mediumID(1), mediumID(2), mediumID(3),
      mediumID(4))
  }

  it should "send unresolved files when a reader actor terminates" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1).createTestActor()
    val esr = enhancedScanResult(1)
    actor ! esr
    val readerActor = helper.expectChildReaderActor()
    readerActor.expectMsgType[PersistentMetaDataReaderActor.ReadMetaDataFile]
    val results = processingResults(1)
    val partialResults = results take 3
    actor ! PersistentMetaDataReaderActor.ProcessingResults(partialResults)
    expectProcessingResults(partialResults)

    system stop readerActor.ref
    val mid = mediumID(1)
    expectMsg(UnresolvedMetaDataFiles(mid, mediumFiles(mid) drop 3, esr))
    helper.writerActor.expectMsg(processMsg(1, 3, esr))
  }

  it should "remove a processed medium from the in-progress map" in {
    val helper = new PersistenceMetaDataManagerActorTestHelper
    val actor = helper.initMediaFiles(1, 2).createTestActor()
    actor ! enhancedScanResult(1, 2)
    val readerActors = helper.expectChildReaderActors(2)
    val mid1 = readerActors.head.expectMsgType[PersistentMetaDataReaderActor.ReadMetaDataFile]
      .mediumID
    val mid2 = readerActors(1).expectMsgType[PersistentMetaDataReaderActor.ReadMetaDataFile]
      .mediumID
    val results = processingResults(mid1) take 4
    actor ! PersistentMetaDataReaderActor.ProcessingResults(results)
    expectProcessingResults(results)
    system stop readerActors.head.ref
    expectMsgType[UnresolvedMetaDataFiles]

    actor ! PersistentMetaDataReaderActor.ProcessingResults(results)
    val results2 = processingResults(mid2) take 3
    actor ! PersistentMetaDataReaderActor.ProcessingResults(results2)
    expectProcessingResults(results2)
  }

  /**
    * A test helper class collecting all dependencies of the test actor.
    *
    * @param skipFileScan if true, the scanning for files is disabled
    */
  private class PersistenceMetaDataManagerActorTestHelper(skipFileScan: Boolean = false) {
    /** A mock for the configuration. */
    val config = createConfig()

    /** A mock for the file scanner. */
    val fileScanner = mock[PersistentMetaDataFileScanner]

    /** Test probe for the child writer actor. */
    val writerActor = TestProbe()

    /** The test actor created by this helper. */
    var managerActor: TestActorRef[PersistentMetaDataManagerActor] = _

    /** A queue for the child actors created by the mock child actor factory. */
    private val childActorQueue = new LinkedBlockingQueue[TestProbe]

    /** A queue with test probes for reader actors. */
    private val testProbes = createTestProbes()

    /**
      * Prepares the mock file scanner to return the media files derived from
      * the passed in indices.
      *
      * @param indices the indices
      * @return this test helper
      */
    def initMediaFiles(indices: Int*): PersistenceMetaDataManagerActorTestHelper = {
      when(fileScanner.scanForMetaDataFiles(FilePath)).thenReturn(persistentFileMapping(indices:
        _*))
      this
    }

    /**
      * Creates a test actor instance. This method should be called after the
      * scanner mock had been initialized.
      *
      * @return the test actor reference
      */
    def createTestActor(): TestActorRef[PersistentMetaDataManagerActor] = {
      managerActor = TestActorRef[PersistentMetaDataManagerActor](createProps())
      managerActor
    }

    /**
      * Expects that the test actor created a new reader actor as child. The
      * probe representing the child is returned.
      *
      * @param timeout a timeout when waiting for the child creation
      * @param unit    the unit for the timeout
      * @return the test probe for the new child actor
      */
    def expectChildReaderActor(timeout: Long = 3, unit: TimeUnit = TimeUnit.SECONDS): TestProbe = {
      val probe = childActorQueue.poll(timeout, unit)
      probe should not be null
      probe
    }

    /**
      * Expects the given number of child reader actors to be created. The
      * corresponding probe objects are returned in a sequence.
      *
      * @param count   the number of child actors
      * @param timeout a timeout when waiting for the child creation
      * @param unit    the unit for the timeout
      * @return a sequence with the probes for the child actors
      */
    def expectChildReaderActors(count: Int, timeout: Long = 3, unit: TimeUnit = TimeUnit.SECONDS)
    : Seq[TestProbe] = {
      @tailrec
      def go(i: Int, children: List[TestProbe]): List[TestProbe] =
        if (i >= count) children
        else go(i + 1, expectChildReaderActor(timeout, unit) :: children)

      go(0, Nil)
    }

    /**
      * Expects that no new child reader actor has been created in the
      * specified timeout.
      *
      * @param timeout a timeout when waiting for the child creation
      * @param unit    the unit for the timeout
      */
    def expectNoChildReaderActor(timeout: Long = 500, unit: TimeUnit = TimeUnit.MILLISECONDS):
    Unit = {
      childActorQueue.poll(timeout, unit) should be(null)
    }

    /**
      * Creates a mock for the configuration.
      *
      * @return the configuration mock
      */
    private def createConfig(): ServerConfig = {
      val config = mock[ServerConfig]
      when(config.metaDataPersistencePath).thenReturn(FilePath)
      when(config.metaDataPersistenceParallelCount).thenReturn(ParallelCount)
      when(config.metaDataPersistenceChunkSize).thenReturn(ChunkSize)
      when(config.metaDataPersistenceWriteBlockSize).thenReturn(WriteBlockSize)
      config
    }

    /**
      * Creates the properties for creating a test actor. Here a child actor
      * factory is specified which returns test probes for reader actors. These
      * children are stored in the queue from where they can be queried.
      *
      * @return creation properties for a test actor instance
      */
    private def createProps(): Props =
      Props(new PersistentMetaDataManagerActor(config, fileScanner) with ChildActorFactory {
        @throws[Exception](classOf[Exception]) override
        def preStart(): Unit = {
          if (!skipFileScan) {
            super.preStart()
          }
        }

        override def createChildActor(p: Props): ActorRef = {
          p.actorClass() match {
            case ClassReaderChildActor =>
              p.args should have length 3
              p.args.head should be(managerActor)
              val parser = p.args(1).asInstanceOf[MetaDataParser]
              parser.chunkParser should be(ParserImpl)
              parser.jsonParser should not be null
              p.args(2) should be(ChunkSize)
              val probe = testProbes.poll()
              childActorQueue put probe
              probe.ref

            case ClassWriterChildActor =>
              p.args should have length 1
              p.args.head should be(WriteBlockSize)
              writerActor.ref
          }
        }
      })

    /**
      * Creates test probes for reader actors. For some strange reasons, the
      * creation of a test probe in the test failed. So they are created
      * beforehand.
      *
      * @return a queue with test probes
      */
    private def createTestProbes(): ArrayBlockingQueue[TestProbe] = {
      val probes = new ArrayBlockingQueue[TestProbe](8)
      for (i <- 1 to 8) probes put TestProbe()
      probes
    }
  }

}
