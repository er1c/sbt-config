lazy val root = (project in file("."))
  .settings(dotDataSettings(publishingEnabled = true))
  .settings(
    name := "sbt-config-test-publishing-enabled",
    version := "0.1",
    scalaVersion := "2.12.12",
    // Default publish / skip is true
    publishLocal := {
      val x = publishArtifact.value
      assert(x == true, s"publishArtifact should be true: $x")
      val skipPublish = (publish / skip).value
      assert(skipPublish == false, s"publish / skip should be false: $skipPublish")
      publishLocal.value
    },
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % Test
  )
