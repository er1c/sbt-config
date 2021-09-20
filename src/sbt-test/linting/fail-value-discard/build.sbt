lazy val root = (project in file("."))
  .settings(dotDataSettings())
  .settings(
    name := "linting-value-discard",
    version := "0.1",
    scalaVersion := "2.12.12",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % Test,
  )
