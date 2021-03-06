import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbt.pgp._
import scalariform.formatter.preferences._
import xerial.sbt.Sonatype._
import sbtrelease.ReleasePlugin
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import pl.project13.scala.sbt.JmhPlugin
import pl.project13.scala.sbt.JmhPlugin.JmhKeys.Jmh

import scala.language.postfixOps

object ScalaCacheBuild extends Build {

  val ScalaVersion = "2.11.8"

  lazy val root = Project(id = "scalacache",base = file("."))
    .enablePlugins(ReleasePlugin)
    .settings(commonSettings: _*)
    .settings(sonatypeSettings: _*)
    .settings(publishArtifact := false)
    .aggregate(core, guava, memcached, ehcache, redis, caffeine)

  lazy val core = Project(id = "scalacache-core", base = file("core"))
    .settings(commonSettings: _*)
    .settings(
      libraryDependencies <+= scalaVersion { s =>
        "org.scala-lang" % "scala-reflect" % s
      }
    )
    .settings(
      libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.5" % Test,
      scala211OnlyDeps(
        "org.squeryl" %% "squeryl" % "0.9.5-7" % Test,
        "com.h2database" % "h2" % "1.4.182" % Test
      )
    )

  lazy val guava = Project(id = "scalacache-guava", base = file("guava"))
    .settings(implProjectSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % "18.0",
        "com.google.code.findbugs" % "jsr305" % "1.3.9"
      )
    )
    .dependsOn(core)

  lazy val memcached = Project(id = "scalacache-memcached", base = file("memcached"))
    .settings(implProjectSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "net.spy" % "spymemcached" % "2.11.7"
      )
    )
    .dependsOn(core % "test->test;compile->compile")

  lazy val ehcache = Project(id = "scalacache-ehcache", base = file("ehcache"))
    .settings(implProjectSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "net.sf.ehcache" % "ehcache" % "2.10.0",
        "javax.transaction" % "jta" % "1.1"
      )
    )
    .dependsOn(core)

  lazy val redis = Project(id = "scalacache-redis", base = file("redis"))
    .settings(implProjectSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "redis.clients" % "jedis" % "2.8.0"
      )
    )
    .settings(playTesting)
    .dependsOn(core % "test->test;compile->compile")

  lazy val caffeine = Project(id = "scalacache-caffeine", base = file("caffeine"))
    .settings(implProjectSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.github.ben-manes.caffeine" % "caffeine" % "2.3.0",
        "com.google.code.findbugs" % "jsr305" % "3.0.0" % "provided"
      )
    )
    .dependsOn(core)

  lazy val benchmarks = Project(id = "benchmarks", base = file("benchmarks"))
    .enablePlugins(JmhPlugin)
    .settings(
      scalaVersion := ScalaVersion,
      publishArtifact := false,
      fork in (Compile, run) := true,
      javaOptions in Jmh ++= Seq("-server", "-Xms2G", "-Xmx2G", "-XX:+UseG1GC", "-XX:-UseBiasedLocking"),
      javaOptions in (Test, run) ++= Seq("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:StartFlightRecording=delay=20s,duration=60s,filename=memoize.jfr", "-server", "-Xms2G", "-Xmx2G", "-XX:+UseG1GC", "-XX:-UseBiasedLocking")
    )
    .dependsOn(caffeine)

  lazy val jodaTime = Seq(
    "joda-time" % "joda-time" % "2.5",
    "org.joda" % "joda-convert" % "1.7"
  )

  lazy val slf4j = Seq(
    "org.slf4j" % "slf4j-api" % "1.7.9"
  )

  lazy val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % "2.2.6" % Test
  )

  val playVersion = "2.3.8"
  lazy val playTesting = scala211OnlyDeps(
    "com.typesafe.play" %% "play-test" % playVersion % Test,
    "org.scalatestplus" %% "play" % "1.2.0" % Test
  )

  // Dependencies common to all projects
  lazy val commonDeps =
    slf4j ++
    scalaTest ++
    jodaTime

  lazy val commonSettings =
    Defaults.coreDefaultSettings ++
    mavenSettings ++
    scalariformSettings ++
    formatterPrefs ++
    Seq(
      organization := "com.github.cb372",
      scalaVersion := ScalaVersion,
      crossScalaVersions := Seq(ScalaVersion, "2.12.0-M4"),
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers += Resolver.typesafeRepo("releases"),
      libraryDependencies ++= commonDeps,
      parallelExecution in Test := false,
      releasePublishArtifactsAction := PgpKeys.publishSigned.value,
      releaseCrossBuild := true,
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        runTest,
        setReleaseVersion,
        commitReleaseVersion,
        updateVersionInReadme,
        tagRelease,
        publishArtifacts,
        setNextVersion,
        commitNextVersion,
        releaseStepCommand("sonatypeReleaseAll"),
        pushChanges
      ),
      commands += Command.command("update-version-in-readme")(updateVersionInReadme)
    )

  lazy val implProjectSettings = commonSettings

  lazy val mavenSettings = Seq(
    pomExtra :=
      <url>https://github.com/cb372/scalacache</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:cb372/scalacache.git</url>
        <connection>scm:git:git@github.com:cb372/scalacache.git</connection>
      </scm>
      <developers>
        <developer>
          <id>cb372</id>
          <name>Chris Birchall</name>
          <url>https://github.com/cb372</url>
        </developer>
      </developers>,
    publishTo <<= version { v =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false }
  )

  // Scalariform preferences
  lazy val formatterPrefs = Seq(
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignParameters, true)
      .setPreference(DoubleIndentClassDeclaration, true)
  )

  lazy val updateVersionInReadme = ReleaseStep(action = st => {
    val extracted = Project.extract(st)
    val projectVersion = extracted.get(Keys.version)

    println(s"Updating project version to $projectVersion in the README")
    Process(Seq("sed", "-i", "", "-E", "-e", s"""s/"scalacache-(.*)" % ".*"/"scalacache-\\1" % "$projectVersion"/g""", "README.md")).!
    println("Committing README.md")
    Process(Seq("git", "commit", "README.md", "-m", s"Update project version in README to $projectVersion")).!

    st
  })

  def scala211OnlyDeps(moduleIDs: ModuleID*) = 
    libraryDependencies ++= (scalaBinaryVersion.value match {
      case "2.11" => moduleIDs
      case other => Nil
    })

}


