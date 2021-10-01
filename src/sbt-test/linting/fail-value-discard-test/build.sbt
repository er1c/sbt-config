lazy val root = project
  .in(file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(dotDataSettings(testConfigurations = Seq(Test, IntegrationTest)))
  .settings(
    name := "linting-fail-value-discard-test",
    version := "0.1",
    scalaVersion := "2.12.12",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % "test,it",
  )
