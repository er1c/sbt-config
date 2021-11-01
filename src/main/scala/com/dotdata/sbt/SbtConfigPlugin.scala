package com.dotdata.sbt

import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import org.scalastyle.sbt.ScalastylePlugin.autoImport._
import scoverage.ScoverageKeys._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import software.amazon.awssdk.auth.credentials.{AwsCredentials, DefaultCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codeartifact.CodeartifactClient
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenRequest

import scala.collection.JavaConverters._

object SbtConfigPlugin extends AutoPlugin {
  object autoImport extends SbtConfigKeys {
    val AssemblyCache = com.dotdata.sbt.AssemblyCache

    object DependencyMode {
      val testAndCompile: String = "test->test;compile->compile"
      val macros: String         = "compile-internal, test-internal"
    }

    sealed trait PublishingLocation
    object PublishingLocation {
      case object DotDataNexus            extends PublishingLocation
      case class GitHub(repoName: String) extends PublishingLocation
      case class CodeArtifact(awsAccountID: String, region: String, domain: String, repoName: String, awsCredentials: Option[AwsCredentials])
          extends PublishingLocation
    }

    implicit class ProjectUtils(project: Project) {
      def dependsOnTestAndCompile(dependencies: Project*): Project = {
        project.dependsOn(dependencies.map(_ % DependencyMode.testAndCompile): _*)
      }
      def dependsOnMacros(dependencies: Project*): Project = {
        project.dependsOn(dependencies.map(_ % DependencyMode.macros): _*)
      }
    }

    // region Compiler settings

    def compilerSettings(versionOfScala: String = "2.12.12"): Def.SettingsDefinition = {
      Seq(
        scalaVersion := versionOfScala,
        fork := true,
        scalacOptions := Seq(
          "-language:higherKinds",
          "-language:implicitConversions",
          "-language:postfixOps",
          "-encoding",
          "utf8"
        )
      )
    }

    // endregion

    // region Formatting

    private def generateScalafmtConf(targetDir: File): File = {
      val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
      val formatConfFile     = targetDir / "scalafmt.conf"

      if (!formatConfFile.exists) {
        IO.withTemporaryFile("scalafmt", "conf") { tmpFile =>
          IO.write(tmpFile, IO.readBytes(scalafmtConfStream))
          IO.move(tmpFile, formatConfFile)
        }
      }

      formatConfFile
    }

    private def generateScalafmtConfTask: Def.Initialize[Task[Seq[File]]] = Def.taskDyn[Seq[File]] {
      val conf = generateScalafmtConf((Compile / resourceManaged).value)
      Def.task(Seq(conf))
    }

    lazy val formatSettings: Def.SettingsDefinition = formatSettings(testConfigurations = Seq(Test))
    def formatSettings(testConfigurations: Seq[Configuration]): Def.SettingsDefinition = {

      val projectSettings = Seq(
        scalafmtConfig := generateScalafmtConf((Compile / resourceManaged).value),
      )

      val configSettings = (Compile +: testConfigurations).flatMap(inConfig(_)(Seq(
        resourceGenerators += generateScalafmtConfTask,
        scalafmt := scalafmt.dependsOn(generateScalafmtConfTask).value,
        scalafmtAll := scalafmtAll.dependsOn(generateScalafmtConfTask).value,
        scalafmtCheckAll := scalafmtCheckAll.dependsOn(generateScalafmtConfTask).value,
        scalafmtOnly := scalafmtOnly.dependsOn(generateScalafmtConfTask).evaluated,
        scalafmtSbt := scalafmtSbt.dependsOn(generateScalafmtConfTask).value
      )))

      val testSettings = testConfigurations.flatMap(inConfig(_) {
        // Test Configuration settings
        Seq(
          // Configuration below for formatting "main" and "test" folders on `sbt test`
          test / testExecution := (test / testExecution)
            .dependsOn(scalafmtCheckAll)
            .dependsOn(Compile / scalafmtCheckAll)
            .dependsOn(generateScalafmtConfTask)
            .value
        )
      })

      projectSettings ++
        configSettings ++
        testSettings
    }

    // endregion

    // region Linting

    private def generateScalastyleConf(targetDir: File): File = {
      val scalaStyleConfigStream = getClass.getClassLoader.getResourceAsStream("scalastyle-config.xml")
      val styleConfigFile        = targetDir / "scalastyle-config.xml"

      if (!styleConfigFile.exists) {
        IO.withTemporaryFile("scalastyle-config", "xml") { tmpFile =>
          IO.write(tmpFile, IO.readBytes(scalaStyleConfigStream))
          IO.move(tmpFile, styleConfigFile)
        }
      }

      styleConfigFile
    }

    private def generateScalastyleConfTask: Def.Initialize[Task[Seq[File]]] = Def.taskDyn[Seq[File]] {
      val conf = generateScalastyleConf((Compile / resourceManaged).value)
      Def.task(Seq(conf))
    }

    def scalastyleSettings(excludes: String = "", testConfigurations: Seq[Configuration] = Seq(Test)): Def.SettingsDefinition = {
      val excludeSettings = if (excludes.nonEmpty) {
        val settings = Seq(
          scalastyleSources := {
            (scalaSource.value ** "*.scala").get.filterNot(_.getAbsolutePath.contains(excludes))
          }
        )
        settings ++ (Compile +: testConfigurations).flatMap(inConfig(_)(settings))
      } else {
        Seq.empty
      }

      val projectSettings = Seq(
        scalastyle := (Compile / scalastyle).dependsOn(generateScalastyleConfTask).evaluated,
        scalastyleConfig := generateScalastyleConf((Compile / resourceManaged).value),
      )

      val configSettings = (Compile +: testConfigurations).flatMap(inConfig(_)(Seq(
        scalastyle := scalastyle.dependsOn(generateScalastyleConfTask).evaluated,
        resourceGenerators += generateScalastyleConfTask,
      )))

      val testSettings = testConfigurations.flatMap(inConfig(_) {
        Seq(
          // Configuration below for formatting "main" and "test" folders on `sbt test`
          test / testExecution :=
            (test / testExecution)
              .dependsOn(scalastyle.toTask(""))
              .dependsOn((Compile / scalastyle).toTask(""))
              .dependsOn(generateScalastyleConfTask)
              .value
        )
      })

      excludeSettings ++
        projectSettings ++
        configSettings ++
        testSettings
    }

    // Deprecations are not immediate and need a notice
    val fatalWarningsExceptDeprecation: Def.SettingsDefinition = fatalWarningsExceptDeprecation(Seq(Compile, Test))

    private val ignoredWarnings = Seq(
      "multiple main classes detected",
      "is deprecated"
    )

    private def fatalWarningsExceptDeprecation(configs: Seq[Configuration]): Def.SettingsDefinition = {
      val settings = Seq(
        compile := {
          val compiled = compile.value
          val problems = compiled.readSourceInfos().getAllSourceInfos.asScala.flatMap {
            case (_, info) =>
              info.getReportedProblems
          }

          val deprecationsOnly = problems.forall { problem =>
            ignoredWarnings.exists { problem.message().contains }
          }

          if (!deprecationsOnly) sys.error("Fatal warnings: some warnings other than deprecations were found.")
          compiled
        }
      )

      configs.flatMap(inConfig(_)(settings))
    }

    val scalacLintingSettings: Seq[String] = Seq(
      "-Xlint:_,-unused,-missing-interpolator",
      "-unchecked",
      "-deprecation",
      "-feature",
      // Later consider "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused-import",
      "-Yno-adapted-args",
      "-Ywarn-unused:_,-explicits,-implicits"
    )

    // Allow some behavior while interactively working on Scala code from the REPL
    private val scalacOptionsConsoleExclusions: Seq[String] = Seq(
      "-Xlint:_,-unused,-missing-interpolator",
      "-unchecked",
      "-Ywarn-unused-import",
      "-Ywarn-unused:_,-explicits,-implicits"
    )

    private val scalacOptionsNotInTest: Seq[String] = Seq(
      "-Ywarn-value-discard"
    )

    def lintingSettings(
       failOnWarnings: Boolean = true,
       scalastyleEnabled: Boolean = true,
       scalastyleExcludes: String = "",
       testConfigurations: Seq[Configuration] = Seq(Test)
    ): Def.SettingsDefinition = {

      val scalacOptionsSettings = {
        // Global + Compile Settings
        Seq(
          scalacOptions ++= scalacLintingSettings,
          Compile / scalacOptions ++= scalacOptionsNotInTest,
          Compile / console / scalacOptions --= scalacOptionsConsoleExclusions,
        ) ++ testConfigurations.flatMap(inConfig(_) {
          // Remove settings from Test configurations
          Seq(
            scalacOptions --= scalacOptionsNotInTest,
            console / scalacOptions --= scalacOptionsConsoleExclusions
          )
        })
      }

      val scalastyleSettingsDef: Def.SettingsDefinition = {
        if (scalastyleEnabled) {
          scalastyleSettings(scalastyleExcludes)
        } else {
          val settings = Seq(
            scalastyle := {}
          )
          settings ++ (Compile +: testConfigurations).flatMap(inConfig(_)(settings))
        }
      }

      if (failOnWarnings) {
        scalacOptionsSettings ++
          fatalWarningsExceptDeprecation(Compile +: testConfigurations) ++
          scalastyleSettingsDef
      } else {
        scalacOptionsSettings ++ scalastyleSettingsDef
      }
    }

    // endregion

    // region Testing

    lazy val testingSettings: Def.SettingsDefinition = Seq(
      // Options from http://www.scalatest.org/user_guide/using_scalatest_with_sbt:
      //  - -o: output to stdout (default, but required for other flags)
      //  - D: Show durations of each test
      //  - F: Show full stack traces
      //  - -u: Causes test results to be written to junit-style xml files in the named directory so CI can pick it up
      testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF", "-u", "target/test-reports")
    )

    def coverageSettings(excludedPackages: String = "", minimumCoverage: Double = 80.00, failOnMinimum: Boolean = true): Def.SettingsDefinition = {
      Seq(
        coverageExcludedPackages := excludedPackages,
        coverageMinimumStmtTotal := minimumCoverage,
        coverageFailOnMinimum := failOnMinimum,
        coverageHighlighting := true
      )
    }

    // endregion

    // region Publishing

    def publishSettings(
      publishingEnabled: Boolean,
      publishingLocation: PublishingLocation,
      testConfigurations: Seq[Configuration] = Seq(Test),
    ): Def.SettingsDefinition = {
      val universalCommonPublishingSettings = {
        val settings = Seq(
          // Don't scan sources during packageDoc
          doc / sources := Nil,
          // Don't publish docs
          packageDoc / publishArtifact := false,
          // Don't aggregate packageDoc
          packageDoc / aggregate := false,
        )

        // Project/Global plus Config Settings
        settings ++ (Compile +: testConfigurations).flatMap(inConfig(_)(settings))
      }

      if (publishingEnabled) {
        val commonPublishingSettings = Seq(
          organization := "com.dotdata",
          publishMavenStyle := true,
          publishArtifact := true
        ) ++ universalCommonPublishingSettings

        commonPublishingSettings ++ {
          publishingLocation match {
            case PublishingLocation.DotDataNexus =>
              Seq(
                publishTo := {
                  // Repository internal caching
                  val nexus = Option(System.getProperty("REPOSITORY_URL")).getOrElse("http://ec2-52-38-203-205.us-west-2.compute.amazonaws.com")
                  if (isSnapshot.value) {
                    Some(("snapshots" at nexus + "/repository/maven-snapshots;build.timestamp=" + new java.util.Date().getTime).withAllowInsecureProtocol(true))
                  } else {
                    Some(("releases" at nexus + "/repository/maven-releases").withAllowInsecureProtocol(true))
                  }
                }
              )
            case PublishingLocation.GitHub(githubRepoName) =>
              Seq(
                publishTo := Some("GitHub Package Registry" at ("https://maven.pkg.github.com/ramencloud/" + githubRepoName)),
                credentials ++= {
                  (sys.env.get("PUBLISH_TO_GITHUB_USERNAME"), sys.env.get("PUBLISH_TO_GITHUB_TOKEN")) match {
                    case (Some(user), Some(pass)) =>
                      Seq(Credentials("GitHub Package Registry", "maven.pkg.github.com", user, pass))
                    case _ => Nil
                  }
                }
              )
            case PublishingLocation.CodeArtifact(awsAccountID, region, domain, repoName, awsCredentials) =>
              Seq(
                publishTo := Some(
                  s"${domain}--${repoName}" at (s"https://${domain}-${awsAccountID}.d.codeartifact.${region}.amazonaws.com/maven/${repoName}/")
                ),
                credentials += {
                  val token = obtainCodeArtifactToken(awsAccountID, region, domain, awsCredentials)
                  Credentials(s"${domain}/${repoName}", s"${domain}-${awsAccountID}.d.codeartifact.${region}.amazonaws.com", "aws", token)
                }
              )
          }
        }
      } else {
        universalCommonPublishingSettings ++ Seq(
          organization := "com.dotdata",
          publishArtifact := false
        )
      }
    }

    // endregion

    // region assembly

    def assemblySettings(
        assemblyEnabled: Boolean = false,
        includeScala: Boolean = true,
        includeBin: Boolean = true,
        includeDependency: Boolean = true,
        testConfigurations: Seq[Configuration] = Seq(Test),
    ): Def.SettingsDefinition = {
      if (assemblyEnabled) {
        // https://www.scala-sbt.org/sbt-native-packager/formats/universal.html#skip-packagedoc-task-on-stage
        val packageDocSettings = {
          val settings = Seq(
            packageDoc / mappings := Nil
          )

          settings ++ (Compile +: testConfigurations).flatMap(inConfig(_)(settings))
        }

        val assemblySettings = Seq(
          assembleArtifact := true,
          assembly / assemblyOption ~= {
            _.withIncludeScala(includeScala)
              .withIncludeBin(includeBin)
              .withIncludeDependency(includeDependency)
          },
          assembly / assemblyMergeStrategy := {
            case "scalafmt.conf" => MergeStrategy.discard
            case "scalastyle-config.xml" => MergeStrategy.discard
            case "application.conf" => MergeStrategy.concat
            case "unwanted.txt" => MergeStrategy.discard
            case x =>
              val oldStrategy = (assembly / assemblyMergeStrategy).value
              oldStrategy(x)
          }
        )

        packageDocSettings ++
          assemblySettings ++
          AssemblyCache.settings
      } else {
        Seq(assembleArtifact := false)
      }
    }

    // endregion

    def dotDataSettings(
        failOnWarnings: Boolean = true,
        testCoverage: Double = 80.00,
        publishingEnabled: Boolean = false,
        publishingLocation: PublishingLocation = PublishingLocation.DotDataNexus,
        scalastyleEnabled: Boolean = true,
        scalastyleExcludes: String = "",
        assemblyEnabled: Boolean = false,
        assemblyIncludeScala: Boolean = true,
        assemblyIncludeBin: Boolean = true,
        assemblyIncludeDependency: Boolean = true,
        testConfigurations: Seq[Configuration] = Seq(Test)
    ): Def.SettingsDefinition = {
      compilerSettings() ++
        formatSettings(testConfigurations) ++
        lintingSettings(failOnWarnings, scalastyleEnabled, scalastyleExcludes, testConfigurations) ++
        testingSettings ++
        coverageSettings(minimumCoverage = testCoverage) ++
        publishSettings(publishingEnabled, publishingLocation) ++
        assemblySettings(
          assemblyEnabled = assemblyEnabled,
          includeScala = assemblyIncludeScala,
          includeBin = assemblyIncludeBin,
          includeDependency = assemblyIncludeDependency,
          testConfigurations = testConfigurations,
        )
    }

    def obtainCodeArtifactToken(awsAccountID: String, region: String, domain: String, awsCredentials: Option[AwsCredentials]): String = {
      val credentialsProvider = awsCredentials match {
        case Some(credentials) => StaticCredentialsProvider.create(credentials)
        case None              => DefaultCredentialsProvider.create()
      }
      val codeArtifact = CodeartifactClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build()
      val request      = GetAuthorizationTokenRequest.builder().domain(domain).domainOwner(awsAccountID).build()
      codeArtifact.getAuthorizationToken(request).authorizationToken()
    }

    private def githubRunNumberBasedVersion(majorVersion: String, mainBranchName: String = "main"): String = {
      if (sys.env.get("GITHUB_REF").contains(s"refs/heads/$mainBranchName")) {
        s"$majorVersion.${sys.env("GITHUB_RUN_NUMBER")}"
      } else {
        val githubVersion =
          for {
            runId <- sys.env.get("GITHUB_RUN_NUMBER")
            sha   <- sys.env.get("GITHUB_SHA")
          } yield {
            s"$majorVersion.$runId-$sha"
          }

        val localDevVersion = s"$majorVersion.0-SNAPSHOT"

        githubVersion.getOrElse(localDevVersion)
      }
    }
  }

}
