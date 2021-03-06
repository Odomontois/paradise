// NOTE: much of this build is copy/pasted from scalameta/scalameta
import java.io._

import scala.util.Try
import scala.xml.{Node => XmlNode, NodeSeq => XmlNodeSeq, _}
import scala.xml.transform.{RewriteRule, RuleTransformer}
import org.scalameta.os
import sbt.{Credentials, Path}

lazy val Scala211 = "2.11.12"
lazy val Scala212 = "2.12.6"
lazy val LanguageVersions = Seq(Scala211, Scala212)
lazy val MetaVersion = "1.8.0"
lazy val LanguageVersion = LanguageVersions.last
version.in(ThisBuild) := "3.0.0-M11"
onLoadMessage := s"Welcome to scalameta/paradise ${version.value}"

// ==========================================
// Projects
// ==========================================

lazy val paradiseRoot = project
  .in(file("."))
  .settings(
    sharedSettings,
    nonPublishableSettings,
    commands += Command.command("ci-release") { state =>
      "very publishSigned" ::
        "sonatypeReleaseAll" ::
        state
    },
    commands += Command.command("ci-test") { state =>
      s"plz $Scala212 paradiseRoot/test" ::
        s"plz $Scala211 paradiseRoot/test" ::
        state
    }
  )
  .aggregate(
    paradise,
    testsCommon,
    testsConverters,
    testsReflect,
    testsMeta
  )

lazy val paradise = project
  .in(file("plugin"))
  .settings(
    publishableSettings,
    description := "Empowers production Scala compiler with latest macro developments",
    mergeSettings,
    isFullCrossVersion,
    libraryDependencies += "org.scalameta" %% "scalameta" % MetaVersion,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    pomPostProcess := { node =>
      new RuleTransformer(new RewriteRule {
        private def isScalametaDependency(node: XmlNode): Boolean = {
          def isArtifactId(node: XmlNode, fn: String => Boolean) =
            node.label == "artifactId" && fn(node.text)
          node.label == "dependency" && node.child.exists(child =>
            isArtifactId(child, _.startsWith("scalameta_")))
        }
        override def transform(node: XmlNode): XmlNodeSeq = node match {
          case e: Elem if isScalametaDependency(node) =>
            Comment("scalameta dependency has been merged into paradise via sbt-assembly")
          case _ => node
        }
      }).transform(node).head
    }
  )

lazy val testsCommon = project
  .in(file("tests/common"))
  .settings(
    sharedSettings,
    nonPublishableSettings,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
  )

lazy val testsConverters = project
  .in(file("tests/converters"))
  .settings(
    sharedSettings,
    nonPublishableSettings,
    exposePaths("testsConverters", Test),
    libraryDependencies += "org.scalameta" %% "testkit" % MetaVersion
  )
  .dependsOn(paradise)

// macro annotation tests, requires a clean on every compile to outsmart incremental compiler.
lazy val testsReflect = project
  .in(file("tests/reflect"))
  .settings(
    sharedSettings,
    usePluginSettings,
    nonPublishableSettings,
    exposePaths("testsReflect", Test)
  )
  .dependsOn(testsCommon)

// macro annotation tests, requires a clean on every compile to outsmart incremental compiler.
lazy val testsMeta = project
  .in(file("tests/meta"))
  .settings(
    sharedSettings,
    usePluginSettings,
    nonPublishableSettings,
    exposePaths("testsMeta", Test)
  )
  .dependsOn(testsCommon)

// ==========================================
// Settings
// ==========================================

lazy val sharedSettings = Def.settings(
  scalaVersion := LanguageVersion,
  crossScalaVersions := LanguageVersions,
  crossVersion := CrossVersion.binary,
  organization := "org.scalameta",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  resolvers += Resolver.sonatypeRepo("releases"),
  libraryDependencies += "org.scalameta" %% "scalameta" % MetaVersion,
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  scalacOptions ++= Seq("-Xfatal-warnings"),
  logBuffered := false,
  updateOptions := updateOptions.value.withCachedResolution(true),
  triggeredMessage.in(ThisBuild) := Watched.clearWhenTriggered
)

lazy val mergeSettings = Def.settings(
  sharedSettings,
  test.in(assembly) := {},
  logLevel.in(assembly) := Level.Error,
  assemblyJarName.in(assembly) :=
    name.value + "_" + scalaVersion.value + "-" + version.value + "-assembly.jar",
  assemblyOption.in(assembly) ~= { _.copy(includeScala = false) },
  Keys.`package`.in(Compile) := {
    val slimJar = Keys.`package`.in(Compile).value
    val fatJar =
      new File(crossTarget.value + "/" + assemblyJarName.in(assembly).value)
    val _ = assembly.value
    IO.copy(List(fatJar -> slimJar), overwrite = true)
    slimJar
  },
  packagedArtifact.in(Compile).in(packageBin) := {
    val temp = packagedArtifact.in(Compile).in(packageBin).value
    val (art, slimJar) = temp
    val fatJar =
      new File(crossTarget.value + "/" + assemblyJarName.in(assembly).value)
    val _ = assembly.value
    IO.copy(List(fatJar -> slimJar), overwrite = true)
    (art, slimJar)
  }
)

lazy val usePluginSettings = Seq(
  scalacOptions ++= {
    val jar = Keys.`package`.in(paradise).in(Compile).value
    System.setProperty("sbt.paths.plugin.jar", jar.getAbsolutePath)
    val addPlugin = "-Xplugin:" + jar.getAbsolutePath
    // Thanks Jason for this cool idea (taken from https://github.com/retronym/boxer)
    // add plugin timestamp to compiler options to trigger recompile of
    // main after editing the plugin. (Otherwise a 'clean' is needed.)
    val dummy = "-Jdummy=" + jar.lastModified
    Seq(addPlugin, dummy)
  }
)

lazy val publishableSettings = Def.settings(
  sharedSettings,
  publishTo :=  {
    val nexus = "http://nexus.tcsbank.ru/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/tcs-snapshot")
    else
      Some("releases" at nexus + "content/repositories/tcs")
  },
  publishArtifact.in(Compile) := true,
  publishArtifact.in(Test) := false,
  publishMavenStyle := true,
  pomIncludeRepository := { x =>
    false
  },
  licenses += "BSD" -> url("https://github.com/scalameta/paradise/blob/master/LICENSE.md"),
  pomExtra := (
    <url>https://github.com/scalameta/paradise</url>
    <inceptionYear>2012</inceptionYear>
    <scm>
      <url>git://github.com/scalameta/paradise.git</url>
      <connection>scm:git:git://github.com/scalameta/paradise.git</connection>
    </scm>
    <issueManagement>
      <system>GitHub</system>
      <url>https://github.com/scalameta/paradise/issues</url>
    </issueManagement>
    <developers>
      <developer>
        <id>odomontois</id>
        <name>Oleg Nizhnik</name>
      </developer>
    </developers>
  )
)

lazy val nonPublishableSettings = Seq(
  publishArtifact := false,
  publish := {}
)

lazy val isFullCrossVersion = Seq(
  crossVersion := CrossVersion.full,
  // NOTE: sbt 0.13.8 provides cross-version support for Scala sources
  // (http://www.scala-sbt.org/0.13/docs/sbt-0.13-Tech-Previews.html#Cross-version+support+for+Scala+sources).
  // Unfortunately, it only includes directories like "scala_2.11" or "scala_2.12",
  // not "scala_2.11.8" or "scala_2.12.1" that we need.
  // That's why we have to work around here.
  unmanagedSourceDirectories in Compile += {
    val base = (sourceDirectory in Compile).value
    base / ("scala-" + scalaVersion.value)
  },
  unmanagedSourceDirectories in Test += {
    val base = (sourceDirectory in Test).value
    base / ("scala-" + scalaVersion.value)
  }
)

def exposePaths(projectName: String, config: Configuration) = {
  def uncapitalize(s: String) =
    if (s.length == 0) ""
    else { val chars = s.toCharArray; chars(0) = chars(0).toLower; new String(chars) }
  val prefix = "sbt.paths." + projectName + "." + uncapitalize(config.name) + "."
  Seq(
    sourceDirectory in config := {
      val defaultValue = (sourceDirectory in config).value
      System.setProperty(prefix + "sources", defaultValue.getAbsolutePath)
      defaultValue
    },
    resourceDirectory in config := {
      val defaultValue = (resourceDirectory in config).value
      System.setProperty(prefix + "resources", defaultValue.getAbsolutePath)
      defaultValue
    },
    fullClasspath in config := {
      val defaultValue = (fullClasspath in config).value
      val classpath = defaultValue.files.map(_.getAbsolutePath)
      val scalaLibrary = classpath.map(_.toString).find(_.contains("scala-library")).get
      System.setProperty("sbt.paths.scalalibrary.classes", scalaLibrary)
      System.setProperty(prefix + "classes", classpath.mkString(java.io.File.pathSeparator))
      defaultValue
    }
  )
}

inScope(Global)(
  Seq(
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
  )
)
