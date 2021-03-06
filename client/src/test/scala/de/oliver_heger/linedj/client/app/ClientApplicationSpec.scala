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

package de.oliver_heger.linedj.client.app

import akka.actor.ActorSystem
import de.oliver_heger.linedj.client.remoting.{ActorFactory, MessageBus, RemoteMessageBus}
import net.sf.jguiraffe.gui.app.ApplicationContext
import net.sf.jguiraffe.gui.platform.javafx.builder.window.StageFactory
import org.apache.commons.configuration.PropertiesConfiguration
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

/**
  * Test class for ''ClientApplication''.
  */
class ClientApplicationSpec extends FlatSpec with Matchers with MockitoSugar with
ApplicationTestSupport {

  /**
    * Creates a test application.
    * @return the test application
    */
  private def createApp(): ClientApplication =
    new ClientApplication("testconfig.xml") with ApplicationSyncStartup

  /**
    * Creates a test application and starts it so that it is correctly
    * initialized.
    * @return the test application
    */
  private def setUpApp(): ClientApplication =
    activateApp(createApp())

  "A ClientApplication" should "use the correct configuration" in {
    val app = setUpApp()

    app.getConfigResourceName should be("testconfig.xml")
  }

  it should "return a correct client context" in {
    val context = mock[ClientApplicationContext]
    val app = createApp()

    app initClientContext context
    app.clientContext should be(context)
    verifyZeroInteractions(context)
  }

  it should "define a bean for the stage factory" in {
    val app = setUpApp()

    queryBean[StageFactory](app, JavaFxSharedWindowManager.BeanStageFactory) should be
    app.clientContext.stageFactory
  }

  it should "define a bean for the actor system" in {
    val app = setUpApp()

    queryBean[ActorSystem](app, ClientApplication.BeanActorSystem) should be(app.clientContext
      .actorSystem)
  }

  it should "define a bean for the actor factory" in {
    val app = setUpApp()

    queryBean[ActorFactory](app, ClientApplication.BeanActorFactory) should be(app.clientContext
      .actorFactory)
  }

  it should "define a bean for the message bus" in {
    val app = setUpApp()

    queryBean[MessageBus](app, ClientApplication.BeanMessageBus) should be(app.clientContext
      .messageBus)
  }

  it should "define a bean for the remote message bus" in {
    val app = setUpApp()

    queryBean[RemoteMessageBus](app, ClientApplication.BeanRemoteMessageBus) should be
    app.clientContext.remoteMessageBus
  }

  it should "query the status of the remote message bus" in {
    val app = createApp()
    val appContext = mock[ApplicationContext]
    val config = new PropertiesConfiguration
    when(appContext.getConfiguration).thenReturn(config)
    val clientContext = new ClientApplicationContextImpl
    app initClientContext clientContext

    app.initGUI(appContext)
    verify(appContext).getConfiguration
    verify(clientContext.remoteMessageBus).queryServerState()
  }
}
