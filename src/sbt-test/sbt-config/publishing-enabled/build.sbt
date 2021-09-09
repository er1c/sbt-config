lazy val root = (project in file("."))
  .settings(dotDataSettings(publishingEnabled = true))
  .settings(
    name := "sbt-config-test-publishing-enabled",
    version := "0.1",
    scalaVersion := "2.12.12",
    // Default publishArtifact is false
    publishLocal := {
      val x = publishArtifact.value
      assert(x == true, s"publishArtifact should be true: $x")
      publishLocal.value
    },
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % Test
  )
