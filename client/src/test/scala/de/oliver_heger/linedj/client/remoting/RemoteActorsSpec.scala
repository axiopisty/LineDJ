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

package de.oliver_heger.linedj.client.remoting

import org.scalatest.{FlatSpec, Matchers}

/**
 * Test class for ''RemoteActors''.
 */
class RemoteActorsSpec extends FlatSpec with Matchers {
  "The RemoteActors enumeration" should "manage a set with all constants" in {
    val constants = RemoteActors.values
    constants should contain only(RemoteActors.MediaManager, RemoteActors.MetaDataManager)
  }
}
