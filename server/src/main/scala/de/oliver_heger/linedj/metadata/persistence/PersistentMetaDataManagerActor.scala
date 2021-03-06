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

import java.nio.file.Path

import akka.actor.{Actor, ActorRef, Props, Terminated}
import de.oliver_heger.linedj.config.ServerConfig
import de.oliver_heger.linedj.media.{EnhancedMediaScanResult, MediumID}
import de.oliver_heger.linedj.metadata.persistence.PersistentMetaDataWriterActor.ProcessMedium
import de.oliver_heger.linedj.metadata.persistence.parser.{JSONParser, MetaDataParser, ParserImpl}
import de.oliver_heger.linedj.metadata.{MetaDataProcessingResult, UnresolvedMetaDataFiles}
import de.oliver_heger.linedj.utils.ChildActorFactory

import scala.annotation.tailrec

object PersistentMetaDataManagerActor {
  /** File extension for meta data files. */
  val MetaDataFileExtension = ".mdt"

  /**
    * An internal message processed by [[PersistentMetaDataManagerActor]] which
    * triggers the scan of the configured directory for meta data files. This
    * message is sent by the actor itself at startup.
    */
  private[persistence] case object ScanForMetaDataFiles

  /**
    * An internally used data class that stores information about media that
    * are currently processed by this actor.
    *
    * @param request       the request for reading the meta data file
    * @param scanResult    the associated scan result
    * @param listenerActor the actor to be notified for results
    * @param resolvedFiles a set with the files that could be resolved
    * @param readerActor   the actor that reads the file for this medium
    */
  private case class MediumData(request: PersistentMetaDataReaderActor.ReadMetaDataFile,
                                scanResult: EnhancedMediaScanResult,
                                listenerActor: ActorRef, resolvedFiles: Set[Path] = Set.empty,
                                readerActor: ActorRef = null) {
    /**
      * Convenience method that returns the ID of the associated medium.
      *
      * @return the medium ID
      */
    def mediumID = request.mediumID

    /**
      * Assigns the specified reader actor to the represented medium.
      *
      * @param reader the reader actor
      * @return the updated instance
      */
    def assignReaderActor(reader: ActorRef): MediumData =
      copy(readerActor = reader)

    /**
      * Updates this instance with processing results that became available.
      * The paths of resolved files are stored so that it is later possible to
      * determine unresolved files.
      *
      * @param results the processing results
      * @return the updated instance
      */
    def updateResolvedFiles(results: Seq[MetaDataProcessingResult]): MediumData =
      copy(resolvedFiles = resolvedFiles ++ results.map(_.path))

    /**
      * Creates an object with information about meta data files that have not
      * been resolved. If there are no unresolved files, result is ''None''.
      *
      * @return the object about unresolved meta data files
      */
    def unresolvedFiles(): Option[UnresolvedMetaDataFiles] = {
      val unresolvedFiles = scanResult.scanResult.mediaFiles(mediumID) filterNot (d => resolvedFiles
        .contains(d.path))
      if (unresolvedFiles.isEmpty) None
      else Some(UnresolvedMetaDataFiles(mediumID = mediumID, result = scanResult,
        files = unresolvedFiles))
    }

    /**
      * Returns the number of files on the represented medium which could be
      * resolved.
      * @return the number of resolved files
      */
    def resolvedFilesCount: Int = resolvedFiles.size
  }

  private class PersistentMetaDataManagerActorImpl(config: ServerConfig,
                                                   fileScanner: PersistentMetaDataFileScanner)
    extends PersistentMetaDataManagerActor(config, fileScanner) with ChildActorFactory

  /**
    * Returns a ''Props'' object for creating an instance of this actor class.
    *
    * @param config the configuration
    * @return creation properties for a new actor instance
    */
  def apply(config: ServerConfig): Props =
    Props(classOf[PersistentMetaDataManagerActorImpl], config, new PersistentMetaDataFileScanner)
}

/**
  * An actor for managing files with media meta data.
  *
  * An instance of this class is responsible for managing persistent meta data
  * files. On startup, this actor scans the meta directory for ''*.mdt'' files
  * associated with the media available. When messages about the media files
  * available are received, it checks whether corresponding meta data files
  * exist. If so, [[PersistentMetaDataReaderActor]] instances are created to
  * read these files. The results of these read operations are then passed
  * back to the calling actor (which is typically the meta data manager actor).
  *
  * Meta data for songs added to the music library is not available initially.
  * Therefore, this actor checks whether persistent meta data is available for
  * a given medium and if it is complete. If this is not the case, the data
  * should be updated. This task is delegated to a specialized child actor.
  * The goal is to generate persistent meta data automatically by storing the
  * information extracted from media files.
  *
  * @param config      the configuration
  * @param fileScanner the scanner for meta data files
  */
class PersistentMetaDataManagerActor(config: ServerConfig,
                                     private[persistence] val fileScanner:
                                     PersistentMetaDataFileScanner) extends Actor {
  this: ChildActorFactory =>

  import PersistentMetaDataManagerActor._

  /** The shared meta data parser. */
  private val parser = new MetaDataParser(ParserImpl, JSONParser.jsonParser(ParserImpl))

  /**
    * Stores information about meta data files available. The data is loaded
    * when the actor is started; so it may no be available immediately.
    */
  private var optMetaDataFiles: Option[Map[String, Path]] = None

  /**
    * A list with scan results sent to this actor. These results can only be
    * processed after information about meta data files is available.
    */
  private var pendingScanResults = List.empty[EnhancedMediaScanResult]

  /**
    * A list with requests for reading meta data files that are waiting to be
    * processed by a reader actor.
    */
  private var pendingReadRequests = List.empty[MediumData]

  /**
    * A map storing information about the media whose data files are currently
    * read. This map stores sufficient information to send notifications about
    * results correctly.
    */
  private var mediaInProgress = Map.empty[MediumID, MediumData]

  /**
    * The child actor for writing meta data for media with incomplete
    * information.
    */
  private var writerActor: ActorRef = _

  /** The current number of active reader actors. */
  private var activeReaderActors = 0

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    writerActor = createChildActor(Props(classOf[PersistentMetaDataWriterActor],
      config.metaDataPersistenceWriteBlockSize))
    self ! ScanForMetaDataFiles
  }

  override def receive: Receive = {
    case ScanForMetaDataFiles =>
      optMetaDataFiles = Some(fileScanner scanForMetaDataFiles config.metaDataPersistencePath)
      processPendingScanResults(pendingScanResults)

    case res: EnhancedMediaScanResult =>
      processPendingScanResults(res :: pendingScanResults)

    case PersistentMetaDataReaderActor.ProcessingResults(data) =>
      val optMediaData = data.headOption.flatMap(mediaInProgress get _.mediumID)
      optMediaData.foreach { d =>
        data foreach d.listenerActor.!
        mediaInProgress = mediaInProgress.updated(d.mediumID, d updateResolvedFiles data)
      }

    case Terminated(reader) =>
      activeReaderActors -= 1
      startReaderActors()

      val optMediumData = mediaInProgress.values find (_.readerActor == reader)
      optMediumData foreach { d =>
        val unresolvedFiles = d.unresolvedFiles()
        unresolvedFiles foreach (processUnresolvedFiles(_, d.listenerActor, d.resolvedFilesCount))
        mediaInProgress = mediaInProgress - d.mediumID
      }
  }

  /**
    * Creates a child actor for reading a meta data file.
    *
    * @return the child reader actor
    */
  private def createChildReaderActor(): ActorRef =
    createChildActor(PersistentMetaDataReaderActor(self, parser, config
      .metaDataPersistenceChunkSize))

  /**
    * Creates a child actor for reading a meta data file and sends it a read
    * request.
    *
    * @param request the request for the child actor
    * @return the child reader actor
    */
  private def createAndStartChildReaderActor(request: PersistentMetaDataReaderActor
  .ReadMetaDataFile): ActorRef = {
    val reader = createChildReaderActor()
    reader ! request
    context watch reader
    reader
  }

  /**
    * Processes the specified pending scan results. The results are grouped
    * using the grouping functions; then the groups are handled accordingly.
    *
    * @param pendingResults the pending results to be processed
    */
  private def processPendingScanResults(pendingResults: List[EnhancedMediaScanResult]): Unit = {
    val (pending, unresolved, requests) = groupPendingScanResults(optMetaDataFiles,
      pendingResults)
    unresolved foreach (processUnresolvedFiles(_, sender(), 0))
    pendingReadRequests = requests ::: pendingReadRequests
    startReaderActors()
    pendingScanResults = pending
  }

  /**
    * Processes an ''UnresolvedMetaDataFiles'' message for a medium for which
    * no meta data file could be found.
    *
    * @param u                the message to be processed
    * @param metaManagerActor the meta data manager actor
    * @param resolved         the number of unresolved files
    */
  private def processUnresolvedFiles(u: UnresolvedMetaDataFiles, metaManagerActor:
  ActorRef, resolved: Int): Unit = {
    metaManagerActor ! u
    writerActor ! createProcessMediumMessage(u, metaManagerActor, resolved)
  }

  /**
    * Creates a ''ProcessMedium'' message based on the specified parameters.
    *
    * @param u                the ''UnresolvedMetaDataFiles'' message
    * @param metaManagerActor the meta data manager actor
    * @param resolved         the number of unresolved files
    * @return the message
    */
  private def createProcessMediumMessage(u: UnresolvedMetaDataFiles, metaManagerActor: ActorRef,
                                         resolved: Int): ProcessMedium = {
    PersistentMetaDataWriterActor.ProcessMedium(mediumID = u.mediumID,
      target = generateMetaDataPath(u), metaDataManager = metaManagerActor, uriPathMapping = u
        .result
        .fileUriMapping, resolvedSize = resolved)
  }

  /**
    * Generates the path for a meta data file based on the specified
    * ''UnresolvedMetaDataFiles'' object.
    *
    * @param u the object describing unresolved files on a medium
    * @return the path for the corresponding meta data file
    */
  private def generateMetaDataPath(u: UnresolvedMetaDataFiles): Path =
    config.metaDataPersistencePath.resolve(u.result.checksumMapping(u.mediumID) +
      MetaDataFileExtension)

/**
    * Starts as many reader actors for meta data files as possible. For each
    * medium request not yet in progress an actor is started until the maximum
    * number of parallel read actors is reached.
    */
  private def startReaderActors(): Unit = {
    val (requests, inProgress, count) = updateMediaInProgress(pendingReadRequests,
      mediaInProgress, activeReaderActors)
    mediaInProgress = inProgress
    pendingReadRequests = requests
    activeReaderActors = count
  }

  /**
    * Groups a list with pending scan results. If meta data files are
    * already available, the media in all scan results are grouped whether a
    * corresponding meta data file exists for them. The resulting lists can be
    * used for further processing.
    *
    * @param optMetaDataFiles an option with meta data files
    * @param scanResults      a list with pending scan results
    * @return the updated list of pending results and lists for further
    *         processing of affected media
    */
  private def groupPendingScanResults(optMetaDataFiles: Option[Map[String, Path]],
                                      scanResults: List[EnhancedMediaScanResult]):
  (List[EnhancedMediaScanResult], List[UnresolvedMetaDataFiles], List[MediumData]) =
    optMetaDataFiles match {
      case Some(map) =>
        val resGroups = scanResults.map(groupMedia(_, map)).unzip
        (Nil, resGroups._1.flatten, resGroups._2.flatten)
      case None =>
        (scanResults, Nil, Nil)
    }

  /**
    * Groups all media in the specified scan result whether a meta data file
    * for them exists or not. The resulting tuple of lists can be used to
    * further process the media.
    *
    * @param res       the current scan result
    * @param dataFiles the map with available data files
    * @return a tuple with sequences about unresolved media and read requests
    */
  private def groupMedia(res: EnhancedMediaScanResult, dataFiles: Map[String, Path]):
  (List[UnresolvedMetaDataFiles], List[MediumData]) = {
    res.scanResult.mediaFiles.foldLeft(
      (List.empty[UnresolvedMetaDataFiles], List.empty[MediumData])) {
      (t, e) => groupMedium(e._1, dataFiles, res, t._1, t._2)
    }
  }

  /**
    * Checks whether for the specified medium a data file exists. By that a
    * grouping of media can be made. To which group a medium is added
    * determines the way it is handled.
    *
    * @param mediumID     the ID of the medium
    * @param dataFiles    the map with available data files
    * @param res          the current scan result
    * @param unresolved   a sequence with unresolved media
    * @param readRequests a sequence with read requests for known media
    * @return a tuple with the updated sequences
    */
  private def groupMedium(mediumID: MediumID, dataFiles: Map[String, Path],
                          res: EnhancedMediaScanResult,
                          unresolved: List[UnresolvedMetaDataFiles],
                          readRequests: List[MediumData]):
  (List[UnresolvedMetaDataFiles], List[MediumData]) =
    dataFiles get res.checksumMapping(mediumID) match {
      case Some(path) =>
        (unresolved, MediumData(request = PersistentMetaDataReaderActor.ReadMetaDataFile(path,
          mediumID),
          scanResult = res, listenerActor = sender()) :: readRequests)
      case None =>
        (UnresolvedMetaDataFiles(mediumID, res.scanResult.mediaFiles(mediumID), res) ::
          unresolved, readRequests)
    }

  /**
    * Updates information about currently processed media. This method is
    * called when new scan data objects arrive or a medium is completely
    * processed. In this case, new read operations may be started. The
    * corresponding data is returned by this method.
    *
    * @param requests    pending read requests
    * @param inProgress  the map with currently processed media
    * @param readerCount the current number of active reader actors
    * @return a tuple with updated information
    */
  @tailrec private def updateMediaInProgress(requests: List[MediumData],
                                             inProgress: Map[MediumID, MediumData], readerCount:
                                             Int): (List[MediumData], Map[MediumID, MediumData],
    Int) =
    requests match {
      case h :: t if readerCount < config.metaDataPersistenceParallelCount =>
        val reader = createAndStartChildReaderActor(h.request)
        updateMediaInProgress(t, inProgress + (h.request.mediumID -> h.assignReaderActor(reader))
          , readerCount + 1)
      case _ => (requests, inProgress, readerCount)
    }
}
