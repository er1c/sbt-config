lazy val root = (project in file("."))
  .settings(
    dotDataSettings(assemblyEnabled = true)
  )
  .settings(
    name := "sbt-config-test-assembly-cache-new-sbt",
    version := "0.1",
    scalaVersion := "2.12.12",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % Test,
    TaskKey[Unit]("check") := {
      val assemblyDir = sbtAssemblyDirectory.value
      if (AssemblyCache.isCIEnv) {
        assert(assemblyDir.isDirectory)
      }
      assert(assemblyCacheUnzip.value)
      assert(assemblyCacheOutput.value)
      assert(assembleArtifact.value)
      ()
    }
  )
