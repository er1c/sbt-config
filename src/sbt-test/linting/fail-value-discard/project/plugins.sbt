resolvers += "sbt-assembly-releases" at "https://maven.pkg.github.com/ramencloud/sbt-assembly"
// This is a special access token that only has public_repo access, not using key with private repo access
val token = sys.env.getOrElse("GITHUB_TOKEN", throw new IllegalArgumentException(s"Need GITHUB_TOKEN set"))
credentials += Credentials("GitHub Package Registry", "maven.pkg.github.com", token, token)

{
  val pluginVersion = System.getProperty("plugin.version")
  if (pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.dotdata" % "sbt-config" % pluginVersion)
}
