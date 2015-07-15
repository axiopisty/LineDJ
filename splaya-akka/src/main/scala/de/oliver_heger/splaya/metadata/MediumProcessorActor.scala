/*
 * Copyright 2015 The Developers Team.
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
package de.oliver_heger.splaya.metadata

import java.io.IOException
import java.nio.file.Path

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import de.oliver_heger.splaya.config.ServerConfig
import de.oliver_heger.splaya.media.{MediumID, MediaFile, MediaScanResult}
import de.oliver_heger.splaya.utils.ChildActorFactory

object MediumProcessorActor {

  private class MediumProcessorActorImpl(data: MediaScanResult, config: ServerConfig)
    extends MediumProcessorActor(data, config) with ChildActorFactory

  /**
   * Returns creation properties for a new actor instance.
   * @param data data about the files to be processed
   * @param config the server configuration
   * @return properties for creating a new actor instance
   */
  def apply(data: MediaScanResult, config: ServerConfig): Props =
    Props(classOf[MediumProcessorActorImpl], data, config)

  /**
   * Produces a sequence with all paths to be processed by this actor based on
   * the scan result passed to the constructor. The result list consists of
   * pairs that map the medium root path to a file to be processed.
   * @param scanResult the scan result
   * @return a sequence with all paths to be processed
   */
  private def pathsToBeProcessed(scanResult: MediaScanResult): List[(Path, MediumID)] = {
    val files = scanResult.mediaFiles.map(e => associateWithMediumID(e._1, e._2)).toList
    files.flatten map (t => (t._1.path, t._2))
  }

  /**
   * Produces a list of pairs that assigns each file to its medium.
   * @param mediumID the medium ID
   * @param files the list of files
   * @return a list of pairs with the associated medium paths
   */
  private def associateWithMediumID(mediumID: MediumID, files: List[MediaFile]): List[
    (MediaFile, MediumID)] = {
    val mediumPathList = List.fill(files.size)(mediumID)
    files zip mediumPathList
  }
}

/**
 * An actor class for extracting the meta data for all media files contained on
 * a medium.
 *
 * When the media server starts up it scans a configurable set of root paths
 * for media files. In a second step, meta data has to be extracted from these
 * files. For each root path, an instance of this actor is created. Thus,
 * multiple paths can be processed in parallel.
 *
 * Meta data extraction for media files is a complex process that requires each
 * file to be read and the data to be processed in various ways. A bunch of
 * actors of different types is involved in this process. Instances of these
 * actor types are mostly created dynamically for the single media files to be
 * processed; so the system adapts dynamically to the current work-load. Only
 * the main reader actors are created initially; their number is defined by a
 * configuration setting. The main responsibility of this actor is to manage
 * all helper actors taking part in the meta data extraction process. They are
 * all children of this actor.
 *
 * The management of multiple actors has the consequence that this actor has to
 * react on a bunch of different messages. However, in most cases, message
 * handling is not complicated: the message just has to be passed to the
 * correct managed actor. Some helper objects are used for creating specific
 * child actors dynamically. For testing purposes, it is possible to pass such
 * objects to the constructor.
 *
 * This actor expects a number of constructor arguments defining the media
 * files to be processed and other meta data related to the extraction
 * process. The actual processing is triggered when a ''Process'' message is
 * received. The sender of this message will also be sent processing results.
 * Multiple messages of this type are ignored.
 *
 * @param data data about the files to be processed
 * @param config the media server configuration
 * @param optMp3ProcessorMap optional map for mp3 processor actors
 * @param optId3v2ProcessorMap optional map for ID3v2 processor actors
 * @param optId3v1ProcessorMap optional map for ID3v1 processor actors
 * @param optCollectorMap optional map for meta data collectors
 */
class MediumProcessorActor(data: MediaScanResult, config: ServerConfig,
                           optMp3ProcessorMap: Option[ProcessorActorMap],
                           optId3v2ProcessorMap: Option[ProcessorActorMap],
                           optId3v1ProcessorMap: Option[ProcessorActorMap],
                           optCollectorMap: Option[MetaDataCollectorMap])
  extends Actor {
  me: ChildActorFactory =>

  import MediumProcessorActor._

  /** The central meta data extraction context. */
  private val extractionContext = new MetaDataExtractionContext(self, config)

  /** The processor actor map for ID3v2 processor actors. */
  private[metadata] val id3v2ProcessorMap = processorActorMap(optId3v2ProcessorMap,
    classOf[ID3FrameProcessorActor])

  /** The processor actor map for MP3 data processor actors. */
  private[metadata] val mp3ProcessorMap = processorActorMap(optMp3ProcessorMap,
    classOf[Mp3DataProcessorActor])

  /** The processor actor map for ID3v1 processor actors. */
  private[metadata] val id3v1ProcessorMap = processorActorMap(optId3v1ProcessorMap,
    classOf[ID3v1FrameProcessorActor])

  /** The map for meta data collectors. */
  private[metadata] val collectorMap = optCollectorMap getOrElse new MetaDataCollectorMap

  /** A map with information about the files currently processed. */
  private val currentProcessingData = collection.mutable.Map.empty[Path, MediumID]

  /**
   * A map keeping track which reader actor processes which file. This is
   * needed for error handling: If a reader fails, cleanup has to be performed,
   * and the currently processed file marked as finished.
   */
  private val readerActorMap = collection.mutable.Map.empty[ActorRef, Path]

  /** A list with all media files to be processed. */
  private var mediaFilesToProcess = pathsToBeProcessed(data)

  /** An option for the meta data manager actor which receives all results. */
  private var metaDataManager: Option[ActorRef] = None

  /**
   * Constructor to be used for default actor creation.
   * @param data data about the files to be processed
   * @param config the media server configuration
   */
  def this(data: MediaScanResult, config: ServerConfig) = this(data, config, None, None, None, None)

  override val supervisorStrategy = OneForOneStrategy() {
    case _: IOException => Stop
  }

  override def receive: Receive = {
    case ProcessMediaFiles =>
      if (metaDataManager.isEmpty) {
        val readerCount = math.min(processorCountFromConfig, mediaFilesToProcess.size)
        for (i <- 0 until readerCount) {
          val reader = createChildReaderActor()
          val fileInfo = mediaFilesToProcess.head
          initiateFileRead(reader, fileInfo)
          mediaFilesToProcess = mediaFilesToProcess.tail
        }
        metaDataManager = Some(sender())
      }

    case msg: ProcessID3FrameData if validPath(msg.path) =>
      id3v2ProcessorMap.getOrCreateActorFor(msg.path, this) ! msg
      collectorMap.getOrCreateCollector(msg.path).expectID3Data()
      if (msg.lastChunk) {
        id3v2ProcessorMap removeItemFor msg.path
      }

    case msg: ProcessMp3Data if validPath(msg.path) =>
      mp3ProcessorMap.getOrCreateActorFor(msg.path, this) ! msg
      id3v1ProcessorMap.getOrCreateActorFor(msg.path, this) ! msg

    case msg: ID3FrameMetaData if validPath(msg.path) =>
      handleProcessingResult(msg.path)(_.addID3Data(msg))

    case ID3v1MetaData(path, metaData) =>
      if (id3v1ProcessorMap.removeItemFor(path).isDefined) {
        handleProcessingResult(path)(_.setID3v1MetaData(metaData))
      }

    case msg: Mp3MetaData =>
      if (mp3ProcessorMap.removeItemFor(msg.path).isDefined) {
        handleProcessingResult(msg.path)(_.setMp3MetaData(msg))
      }

    case MediaFileRead(_) =>
      readerActorMap remove sender()
      processNextFile(sender())

    case t: Terminated =>
      readerActorMap remove t.actor foreach handleTerminatedReadActor
  }

  /**
   * Creates a new child actor for reading media files.
   * @return the new child reader actor
   */
  private def createChildReaderActor(): ActorRef = {
    val reader = createChildActor(Mp3FileReaderActor(extractionContext))
    context watch reader
    reader
  }

  /**
   * Starts reading a new media file. Some status variables are updated
   * accordingly.
   * @param reader the reader actor
   * @param fileInfo the object for the file to be read
   */
  private def initiateFileRead(reader: ActorRef, fileInfo: (Path, MediumID)): Unit = {
    reader ! ReadMediaFile(fileInfo._1)
    currentProcessingData += fileInfo
    readerActorMap += (reader -> fileInfo._1)
  }

  /**
   * Tells the specified actor to start processing of the next file in the list
   * (if available).
   * @param reader the reader actor
   */
  private def processNextFile(reader: ActorRef): Unit = {
    mediaFilesToProcess.headOption foreach { t =>
      initiateFileRead(reader, t)
      mediaFilesToProcess = mediaFilesToProcess.tail
    }
  }

  /**
   * Handles a meta data processing result that came in. This method checks
   * whether now all meta data for the specified file is available. If so, the
   * meta data manager is notified. The sending actor is stopped.
   * @param p the path to the file the meta data belongs to
   * @param f a function for updating the collected meta data
   */
  private def handleProcessingResult(p: Path)(f: MetaDataPartsCollector => Option[MediaMetaData])
  : Unit = {
    for {manager <- metaDataManager
         metaData <- f(collectorMap.getOrCreateCollector(p))
    } {
      sendProcessingResult(manager, p, metaData)

      if (currentProcessingData.isEmpty && mediaFilesToProcess.isEmpty) {
        manager ! MediaFilesProcessed(data)
      }
    }
    context stop sender()
  }

  /**
   * Sends a processing result message to the manager actor and updates some
   * internal fields indicating that the given path has now been processed.
   * @param manager the meta data manager actor
   * @param p the path
   * @param metaData the meta data to be sent
   */
  private def sendProcessingResult(manager: ActorRef, p: Path, metaData: MediaMetaData): Unit = {
    manager ! MetaDataProcessingResult(p, currentProcessingData(p), metaData)
    collectorMap removeItemFor p
    currentProcessingData -= p
  }

  /**
   * Handles a crashed read actor. In this case, some cleanup has to be done
   * for the affected file. An empty result message is sent to the manager
   * actor. A new child reader actor is created replacing the crashed one.
   * @param p the path of the file processed by the crashed actor
   */
  private def handleTerminatedReadActor(p: Path): Unit = {
    List(mp3ProcessorMap, id3v1ProcessorMap, id3v2ProcessorMap) foreach { m =>
      m removeItemFor p foreach context.stop
    }
    metaDataManager foreach (sendProcessingResult(_, p, MediaMetaData()))
    processNextFile(createChildReaderActor())
  }

  /**
   * Checks whether the given path is valid, i.e. it is currently processed.
   * @param path the path to be checked
   * @return a flag whether this is a valid path
   */
  private def validPath(path: Path) = currentProcessingData contains path

  /**
   * Obtains the ''ProcessorActorMap'' for a specific actor class. If it has
   * been defined explicitly, it is used. Otherwise, a new map is created based
   * on the given parameters.
   * @param optMap an option for the map
   * @param actorClass the actor class
   * @return the resulting ''ProcessorActorMap''
   */
  private def processorActorMap(optMap: Option[ProcessorActorMap], actorClass: Class[_]):
  ProcessorActorMap =
    optMap getOrElse new ProcessorActorMap(Props(actorClass, extractionContext))

  /**
   * Obtains the number of reader actors from the configuration. Handles an
   * unknown root path.
   * @return the number of processing actors for the path to be processed
   */
  private def processorCountFromConfig: Int = {
    config.rootFor(data.root) map (_.processorCount) getOrElse 1
  }
}
