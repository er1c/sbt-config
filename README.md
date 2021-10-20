# dotData sbt config

![Build Status](https://github.com/ramencloud/sbt-config/workflows/Continuous%20Integration/badge.svg)

## Adding the plugin to your project

### project/plugins.sbt


Accessing GitHub Packages requires a valid GITHUB_TOKEN.  It needs to be created with: `public_repo, read:packages` permissions.

    resolvers +=  "sbt-config-releases" at "https://maven.pkg.github.com/ramencloud/sbt-config"
    credentials += Credentials("GitHub Package Registry", "maven.pkg.github.com", "<github-user>", "<GITHUB_TOKEN>")
    addSbtPlugin("com.dotdata" % "sbt-config" % "<latest_version>")

Run `sbt reload` after adding the plugin.

### build.sbt

dotDataSettings defaults:

    dotDataSettings(
      failOnWarnings: Boolean = true,
      testCoverage: Double = 80.00,
      publishingEnabled: Boolean = false
    )

To use all standard settings, use the following format:

    lazy val myProject = (project in file("."))
      .settings(dotDataSettings())
      .settings(... other settings ...

To enable publishing with other default settings:

    lazy val myProject = (project in file("."))
      .settings(dotDataSettings(publishingEnabled = true))
      .settings(... other settings ...

To enable sbt-assembly with other default settings:

    lazy val myProject = (project in file("."))
      .settings(dotDataSettings(assemblyEnabled = true))
      .settings(... other settings ...

To opt-out from some settings, use the following format:

    lazy val myProjectWithCustomizations = (project in file("."))
      .settings(compilerSettings())
      .settings(formatSettings ++ lintingSettings() ++ testingSettings)
      .settings(coverageSettings(minimumCoverage = 80.00))
      .settings(publishSettings(publishingEnabled = false))
      .settings(... other settings ...

## References

This sbt plugin is inspired by [https://github.com/driver-oss/sbt-settings](https://github.com/driver-oss/sbt-settings).
