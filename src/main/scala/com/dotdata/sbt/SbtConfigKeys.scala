package com.dotdata.sbt

import sbt._

trait SbtConfigKeys {
  lazy val sbtAssemblyDirectory = taskKey[File]("Directory to cache sbt assembly output.")
}

object SbtConfigKeys extends SbtConfigKeys
