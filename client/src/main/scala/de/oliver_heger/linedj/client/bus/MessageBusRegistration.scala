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

import de.oliver_heger.linedj.client.remoting.MessageBus

/**
 * A class that handles the registration of message bus listeners.
 *
 * This class is used to allow the declaration of message bus listeners in UI
 * or bean definition files. The idea is that a bean of this class is declared
 * which is passed a collection of all [[MessageBusListener]] objects to be
 * registered. This instance gets further injected the message bus object, so
 * that it can perform the listener registration.
 *
 * During application startup it just has to be ensured that the
 * ''MessageBusRegistration'' bean instance is queried from the bean context;
 * this ensures the creation of all referenced bus listeners and their
 * registration.
 *
 * @param listeners a collection with the listeners to be registered
 */
class MessageBusRegistration(listeners: java.util.Collection[MessageBusListener]) {
  /**
   * Injection method for the message bus. This method (with this bean-like
   * signature) is called by the dependency injection framework to inject a
   * reference to the central message bus. Despite the naming convention, the
   * bus is not stored, but all managed bus listeners are registered.
   * @param bus the ''MessageBus''
   */
  def setMessageBus(bus: MessageBus): Unit = {
    import collection.JavaConversions._

    listeners foreach { l =>
      bus registerListener l.receive
    }
  }
}
