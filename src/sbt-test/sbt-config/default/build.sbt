lazy val root = (project in file("."))
  .settings(dotDataSettings())
  .settings(
    name := "sbt-config-test-default",
    version := "0.1",
    scalaVersion := "2.12.12",
    // Default publish / skip is true
    publishLocal := {
      val x = (publish / skip).value
      assert(x == true, s"publish / skip should be true: $x")
    },
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % Test,
    TaskKey[Unit]("check") := {
      assert(!assembleArtifact.value)
      ()
    }
  )
