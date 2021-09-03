package com.dotdata.sbt

import _root_.sbt._
import _root_.sbt.Keys._
import _root_.sbt.internal.util.Util
import sbtassembly.AssemblyPlugin.autoImport._

import java.util.Locale
import scala.sys.process._
import scala.reflect.runtime.universe
import scala.util.Try

object AssemblyCache extends SbtConfigKeys {
  // region sbt-shim

  /** This provides a shim for pre-sbt 1.5.x+ branches */
  private val sbtShimLocalCacheDirectory = settingKey[File]("Directory to pull the remote cache to.")

  /** sbt back-port of isMac for sbt.internal.util.Util
    * Ref: https://github.com/sbt/sbt/blob/master/internal/util-collection/src/main/scala/sbt/internal/util/Util.scala#L50
    */
  private object RichSbtUtil {
    private lazy val isMac: Boolean =
      System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("mac")
  }

  private implicit class RichSbtUtil(val util: Util.type) extends AnyVal {
    def isMac: Boolean = RichSbtUtil.isMac
  }

  private def home: File = file(sys.props("user.home"))

  /** sbt back port for globalLocalCache
    * Ref: https://github.com/sbt/sbt/blob/master/main/src/main/scala/sbt/internal/SysProp.scala#L188
    */
  private def globalLocalCache: File = {
    val appName                    = "sbt"
    def propCacheDir: Option[File] = sys.props.get("sbt.global.localcache").map(file)
    def propCacheDir2: Option[File] =
      sys.props.get(BuildPaths.GlobalBaseProperty) match {
        case Some(base) => Some(file(base) / "cache")
        case _          => None
      }
    def envCacheDir: Option[File] = sys.env.get("SBT_LOCAL_CACHE").map(file)
    def windowsCacheDir: Option[File] =
      sys.env.get("LOCALAPPDATA") match {
        case Some(app) if Util.isWindows => Some(file(app) / appName)
        case _                           => None
      }
    def macCacheDir: Option[File] =
      if (Util.isMac) {
        Some(home / "Library" / "Caches" / appName)
      } else {
        None
      }
    def linuxCache: File =
      sys.env.get("XDG_CACHE_HOME") match {
        case Some(cache) => file(cache) / appName
        case _           => home / ".cache" / appName
      }
    def baseCache: File =
      propCacheDir
        .orElse(propCacheDir2)
        .orElse(envCacheDir)
        .orElse(windowsCacheDir)
        .orElse(macCacheDir)
        .getOrElse(linuxCache)
    baseCache.getAbsoluteFile / "v1"
  }

  // endregion

  /** For Global Scope sbt settings */
  def settings: Seq[Def.Setting[_]] = {
    Seq(
      // Initialize the shim if on older sbt version, otherwise use existing value
      sbtShimLocalCacheDirectory := AssemblyCache.globalLocalCache,
      localCacheDirectory := {
        if (sbtHasLocalCacheDirectory(sbtVersion.value)) localCacheDirectory.value
        else sbtShimLocalCacheDirectory.value
      },
      sbtAssemblyDirectory := {
        // k9lib/workspace/build_env sets CONTAINER_REPO_ROOT_DIR to /shared
        val repoRoot: String =
          sys.env.get("CONTAINER_REPO_ROOT_DIR") orElse
            Try(("git rev-parse --show-toplevel".!!).trim).toOption getOrElse
            ((LocalRootProject / baseDirectory).value / "..").getCanonicalPath

        val relativeWorkDir =
          baseDirectory.value.getCanonicalPath
            .stripPrefix(repoRoot)
            .stripPrefix(java.io.File.pathSeparator)
        val canonicalBranch = sys.env.getOrElse("BRANCH_NAME_CANONICAL", "master")
        val sbtAssemblyLocalCacheDir: File =
          localCacheDirectory.value / "assembly" / canonicalBranch / relativeWorkDir

        // (e.g. ~/.sbt/cache/assembly/MVP-6/... (Linux), ~/Library/Caches/sbt/assembly/MVP-6/... (OSX))
        sbtAssemblyLocalCacheDir
      },
      // Cache sbt assembly intermediate files in a shared location instead of target/
      assembly / assemblyOption := {
        val opt = (assembly / assemblyOption).value
        val dir = sbtAssemblyDirectory.value
        opt
          .withAssemblyDirectory(dir)
          // These are defaults, but adding for readability and explicitness
          .withCacheUnzip(true)  // Keep dependency .jar files pre-extracted
          .withCacheOutput(true) // Cache pre-merged assembly file .class files to diff changes
      }
    )
  }

  def settings(configuration: Configuration): Seq[Def.Setting[_]] = inConfig(configuration)(settings)

  private def sbtHasLocalCacheDirectory(sbtVersion: String): Boolean = CrossVersion.partialVersion(sbtVersion) match {
    case Some((1, minor)) if minor >= 5 => true
    case Some((1, minor)) if minor < 5  => false
    case _                              => sys.error(s"Unsupported sbtVersion: ${sbtVersion}")
  }

  private val localCacheDirectory: SettingKey[File] = loadIfExists(
    fullyQualifiedName = "sbt.Keys.localCacheDirectory",
    args = None,
    default = sbtShimLocalCacheDirectory
  )

  /**
    * From: https://github.com/etspaceman/kinesis-mock/blob/a7d94e74d367b74479f565fa9c5b5692e4d1b8fd/project/BloopSettings.scala#L8
    * License: MIT
    *
    * MIT License
    *
    * Copyright (c) 2021 Eric Meisel
    *
    * Permission is hereby granted, free of charge, to any person obtaining a copy
    * of this software and associated documentation files (the "Software"), to deal
    * in the Software without restriction, including without limitation the rights
    * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    * copies of the Software, and to permit persons to whom the Software is
    * furnished to do so, subject to the following conditions:
    *
    * The above copyright notice and this permission notice shall be included in all
    * copies or substantial portions of the Software.
    * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    * SOFTWARE.
    *
    * Example Usage:
    * {{{
    * val default: Seq[Def.Setting[_]] = loadIfExists(
    *   fullyQualifiedName = "bloop.integrations.sbt.BloopDefaults.configSettings",
    *   args = Some(Nil),
    *   default = Seq.empty[Def.Setting[_]]
    * )
    * }}}
    *
    * @param fullyQualifiedName
    * @param args
    * @param default
    * @tparam T
    * @return
    */
  private def loadIfExists[T](
      fullyQualifiedName: String,
      args: Option[Seq[Any]],
      default: => T
  ): T = {
    val tokens     = fullyQualifiedName.split('.')
    val memberName = tokens.last
    val moduleName = tokens.take(tokens.length - 1).mkString(".")

    val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
    val value = Try(runtimeMirror.staticModule(moduleName)).map { module =>
      val obj            = runtimeMirror.reflectModule(module)
      val instance       = obj.instance
      val instanceMirror = runtimeMirror.reflect(instance)
      val member =
        instanceMirror.symbol.info.member(universe.TermName(memberName))
      args
        .fold(instanceMirror.reflectField(member.asTerm).get)(args => instanceMirror.reflectMethod(member.asMethod)(args: _*))
        .asInstanceOf[T]
    }
    value.getOrElse(default)
  }
}
