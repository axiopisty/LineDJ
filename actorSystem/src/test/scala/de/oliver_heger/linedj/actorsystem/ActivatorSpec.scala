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

package de.oliver_heger.linedj.actorsystem

import akka.actor.ActorSystem
import org.osgi.framework.BundleContext
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}

/**
  * Test class for ''Activator''.
  */
class ActivatorSpec extends FlatSpec with Matchers with MockitoSugar {
  "An Activator" should "register the actor system" in {
    val context = mock[BundleContext]
    val system = mock[ActorSystem]
    val activator = new Activator

    activator.configure(context, system)
    verify(context).registerService(classOf[ActorSystem], system, null)
  }
}
