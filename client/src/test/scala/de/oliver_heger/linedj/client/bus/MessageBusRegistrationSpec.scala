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

package de.oliver_heger.linedj.client.bus

import akka.actor.Actor
import de.oliver_heger.linedj.client.remoting.MessageBus
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

/**
 * Test class for ''MessageBusRegistration''.
 */
class MessageBusRegistrationSpec extends FlatSpec with Matchers with MockitoSugar {
  /**
   * Creates a mock for a message bus listener together with a mock for the
   * message handling function.
   * @return the mocks for the listener and the message function
   */
  private def createListenerMock(): (MessageBusListener, Actor.Receive) = {
    val listener = mock[MessageBusListener]
    val rec = mock[Actor.Receive]
    when(listener.receive).thenReturn(rec)
    (listener, rec)
  }

  /**
   * Creates a map with some test bus listeners and their message handling
   * functions.
   * @return the map with test listeners
   */
  private def createListeners(): Map[MessageBusListener, Actor.Receive] = {
    val listeners = 1 to 5 map (i => createListenerMock())
    Map(listeners: _*)
  }

  "A MessageBusRegistration" should "register all message bus listeners" in {
    import collection.JavaConversions._
    val bus = mock[MessageBus]
    val listenerMap = createListeners()
    val registration = new MessageBusRegistration(listenerMap.keySet)

    registration setMessageBus bus
    listenerMap.values foreach verify(bus).registerListener
  }
}
