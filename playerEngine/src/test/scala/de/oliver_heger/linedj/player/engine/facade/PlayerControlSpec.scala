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

package de.oliver_heger.linedj.player.engine.facade

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.AskTimeoutException
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import de.oliver_heger.linedj.io.{CloseAck, CloseRequest}
import de.oliver_heger.linedj.player.engine.impl.{LineWriterActor, PlaybackActor}
import de.oliver_heger.linedj.player.engine.{PlaybackContextFactory, PlayerConfig}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * Test class for ''PlayerControl''.
  */
class PlayerControlSpec(testSystem: ActorSystem) extends TestKit(testSystem) with FlatSpecLike
  with BeforeAndAfterAll with Matchers with MockitoSugar {
  def this() = this(ActorSystem("PlayerControlSpec"))

  override protected def afterAll(): Unit = {
    TestKit shutdownActorSystem system
  }

  "A PlayerControl" should "allow adding a playback context factory" in {
    val factory = mock[PlaybackContextFactory]
    val helper = new PlayerControlTestHelper
    val player = helper.createPlayerControl()

    player addPlaybackContextFactory factory
    helper.probePlaybackActor.expectMsg(PlaybackActor.AddPlaybackContextFactory(factory))
  }

  it should "allow removing a playback context factory" in {
    val factory = mock[PlaybackContextFactory]
    val helper = new PlayerControlTestHelper
    val player = helper.createPlayerControl()

    player removePlaybackContextFactory factory
    helper.probePlaybackActor.expectMsg(PlaybackActor.RemovePlaybackContextFactory(factory))
  }

  it should "allow starting playback" in {
    val helper = new PlayerControlTestHelper
    val player = helper.createPlayerControl()

    player.startPlayback()
    helper.probePlaybackActor.expectMsg(PlaybackActor.StartPlayback)
  }

  it should "allow stopping playback" in {
    val helper = new PlayerControlTestHelper
    val player = helper.createPlayerControl()

    player.stopPlayback()
    helper.probePlaybackActor.expectMsg(PlaybackActor.StopPlayback)
  }

  it should "allow skipping the current source" in {
    val helper = new PlayerControlTestHelper
    val player = helper.createPlayerControl()

    player.skipCurrentSource()
    helper.probePlaybackActor.expectMsg(PlaybackActor.SkipSource)
  }

  it should "create Props for the line writer when no blocking dispatcher is defined" in {
    val config = PlayerConfig(mediaManagerActor = null, actorCreator = (props, name) => null)

    PlayerControl createLineWriterActorProps config should be(Props[LineWriterActor])
  }

  /**
    * Creates a list of test actors that just react on a close request by
    * sending the corresponding ACK.
    *
    * @param count the number of test actors to create
    * @param closeCounter a counter for recording close requests
    * @return the list with test actors
    */
  private def createCloseTestActors(count: Int, closeCounter: AtomicInteger): IndexedSeq[ActorRef] =
    (1 to count) map(i => system.actorOf(Props(new Actor {
      override def receive: Receive = {
        case CloseRequest =>
          sender ! CloseAck(self)
          closeCounter.incrementAndGet()
      }
    } )))

  it should "provide a method to close dependent actors" in {
    val helper = new PlayerControlTestHelper
    val player = helper.createPlayerControl()
    val counter = new AtomicInteger

    val probes = createCloseTestActors(3, counter)
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(1.second)
    val handle = player.closeActors(probes)
    val result = Await.result(handle, 1.second)
    counter.get() should be(probes.size)
    result map (_.actor) should contain theSameElementsAs probes
  }

  it should "do correct timeout handling in its closeActors() method" in {
    val helper = new PlayerControlTestHelper
    val player = helper.createPlayerControl()
    val latch = new CountDownLatch(1)

    val probes = createCloseTestActors(2, new AtomicInteger).toList
    val timeoutProbe = TestProbe()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(200.milliseconds)
    player.closeActors(timeoutProbe.ref :: probes).onFailure {
      case _: AskTimeoutException => latch.countDown()
    }
    latch.await(1, TimeUnit.SECONDS) shouldBe true
  }

  /**
    * A test helper class managing some dependencies of the test class. It also
    * provides a concrete implementation of the trait under test.
    */
  private class PlayerControlTestHelper {
    /** The test playback actor. */
    val probePlaybackActor = TestProbe()

    /**
      * Creates a test instance of ''PlayerControl''.
      *
      * @return the test instance
      */
    def createPlayerControl(): PlayerControlImpl = new PlayerControlImpl(probePlaybackActor.ref)
  }
}

/**
  * A test implementation of the trait which wraps the specified actor.
  *
  * @param playbackActor the playback actor
  */
private class PlayerControlImpl(override val playbackActor: ActorRef) extends PlayerControl {
  override def closeActors(actors: Seq[ActorRef])(implicit ec: ExecutionContext, timeout:
  Timeout): Future[Seq[CloseAck]] = super.closeActors(actors)

  override def close()(implicit ec: ExecutionContext, timeout: Timeout): Future[scala
  .Seq[CloseAck]] = {
    throw new UnsupportedOperationException("Unexpected invocation!")
  }
}
