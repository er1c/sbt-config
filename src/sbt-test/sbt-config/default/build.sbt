lazy val root = (project in file("."))
  .settings(dotDataSettings())
  .settings(
    name := "sbt-config-test-default",
    version := "0.1",
    scalaVersion := "2.12.12",
    // Default publishArtifact is false
    publishLocal := {
      val x = publishArtifact.value
      assert(x == false, s"publishArtifact should be false: $x")
    },
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % Test,
    TaskKey[Unit]("check") := {
      assert(!assembleArtifact.value)
      ()
    }
  )
