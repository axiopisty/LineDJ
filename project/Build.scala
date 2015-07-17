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
import sbt._
import Keys._

object HelloBuild extends Build {
    lazy val akkaDependencies = Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.3.4",
      "com.typesafe.akka" %% "akka-testkit" % "2.3.4"
    )
    
    lazy val testDependencies = Seq(
      "org.scalatest" %% "scalatest" % "2.1.6" % "test",
      "junit" % "junit" % "4.12" % "test",
      "org.mockito" % "mockito-core" % "1.9.5" % "test"
    )
    
    val defaultSettings = Seq(
        version := "1.0-SNAPSHOT",
        scalaVersion := "2.11.5",
        libraryDependencies ++= akkaDependencies,
        libraryDependencies ++= testDependencies
    )

    lazy val root = Project(id = "LineDJ",
                            base = file(".")) aggregate(server)
      .settings(defaultSettings: _*)
      .settings(
        name := "LineDJ Parent"
      )

    lazy val server = Project(id = "server",
                           base = file("splaya-akka"))
      .settings(defaultSettings: _*)
      .settings(
        name := "LineDJ Server",
        libraryDependencies ++= Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
          "org.slf4j" % "slf4j-api" % "1.7.10",
          "org.slf4j" % "slf4j-simple" % "1.7.10" % "test"
        )
      )
}

