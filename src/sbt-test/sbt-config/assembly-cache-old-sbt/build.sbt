lazy val root = (project in file("."))
  .settings(
    dotDataSettings() ++ assemblySettings(assemblyEnabled = true)
  )
  .settings(
    name := "sbt-config-test-assembly-cache-old-sbt",
    version := "0.1",
    scalaVersion := "2.12.12",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % Test,
    TaskKey[Unit]("check") := {
      assert(sbtAssemblyDirectory.value.isDirectory)
      assert(assemblyCacheUnzip.value)
      assert(assemblyCacheOutput.value)
      assert(assembleArtifact.value)
      ()
    }
  )
