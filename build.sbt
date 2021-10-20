sbtPlugin := true

organization := "com.dotdata"
name := "sbt-config"
scalaVersion := "2.12.12"
homepage := Some(url("https://github.com/ramencloud/sbt-config"))
licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

val baseVersion = "0.1"

resolvers += "sbt-assembly-releases" at "https://maven.pkg.github.com/ramencloud/sbt-assembly"

version := {
  if (sys.env.get("GITHUB_REF").contains("refs/heads/master")) {
    s"$baseVersion.${sys.env("GITHUB_RUN_NUMBER")}"
  } else {
    val githubVersion =
      for {
        runId <- sys.env.get("GITHUB_RUN_NUMBER")
        sha <- sys.env.get("GITHUB_SHA")
      } yield {
        s"$baseVersion.$runId-$sha"
      }

    val localDevVersion = s"$baseVersion.0-SNAPSHOT"

    githubVersion.getOrElse(localDevVersion)
  }
}

publishMavenStyle := true

publishTo := Some("GitHub Package Registry" at "https://maven.pkg.github.com/ramencloud/sbt-config")
credentials += {
  (sys.env.get("PUBLISH_TO_GITHUB_USERNAME"), sys.env.get("PUBLISH_TO_GITHUB_TOKEN")) match {
    case (Some(user), Some(pass)) =>
      Credentials("GitHub Package Registry", "maven.pkg.github.com", user, pass)
    case _ =>
      val token = sys.env.getOrElse("GITHUB_TOKEN", throw new IllegalArgumentException(s"Need GITHUB_TOKEN set"))
      Credentials("GitHub Package Registry", "maven.pkg.github.com", token, token)
  }
}

scmInfo := Some(
  ScmInfo(
    url("https://github.com/ramencloud/sbt-config"),
    "scm:git:git@github.com:ramencloud/sbt-config.git",
    "scm:git:https://github.com/ramencloud/sbt-config.git"
  )
)

enablePlugins(SbtPlugin)

addSbtPlugin("com.eed3si9n"   % "sbt-assembly"           % "1.2.0-DOTDATA")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"           % "2.4.2")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scoverage"  %% "sbt-scoverage"         % "1.8.2")

// Make the build faster, since there is no Scaladocs anyway
sources in (Compile, doc) := Seq.empty
publishArtifact in (Compile, packageDoc) := false

// the following prevents thousands of meaningless stacktraces by docker plugin on JDK 9
libraryDependencies += "javax.activation" % "activation" % "1.1.1" % Test

// Make sure to publish the library locally first
scripted := scripted.dependsOn(publishLocal).evaluated

// For running integration tests in src/sbt-test
scriptedLaunchOpts := scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
scriptedBufferLog := false
