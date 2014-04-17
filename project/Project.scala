import sbt._
import Keys._
import com.github.mkroli.webresources._


object BuildSettings {
  val buildOrganization = "org.scalanlp"
  val buildScalaVersion = "2.10.3"
  val buildVersion = "0.1-SNAPSHOT"


  val buildSettings = Defaults.defaultSettings ++ Seq (
    version := buildVersion,
    organization := buildOrganization,
    scalaVersion := buildScalaVersion,
    resolvers ++= Seq(
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "FormReturn" at "http://maven.formreturn.com/repository/"
    ),
    crossScalaVersions := Seq("2.10.2"),
  publishMavenStyle := true,
  publishTo <<= version { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) 
      Some("snapshots" at nexus + "content/repositories/snapshots") 
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := (
    <url>http://scalanlp.org/</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:dlwh/breeze.git</url>
      <connection>scm:git:git@github.com:dlwh/breeze.git</connection>
    </scm>
    <developers>
      <developer>
        <id>dlwh</id>
        <name>David Hall</name>
        <url>http://cs.berkeley.edu/~dlwh/</url>
      </developer>
    </developers>),
  scalacOptions ++= Seq("-optimize","-deprecation","-language:_"),
    javacOptions ++= Seq("-target", "1.6", "-source","1.6")
  )
}


object EpicBuild extends Build {
  import BuildSettings._
  import WebResources.{buildSettings => _, _}

  val epic = "org.scalanlp" %% "epic" % "0.1-SNAPSHOT"

  val deps = Seq(epic)

  val modelsURL = "http://www.scalanlp.org/resources/"

  def downloadModel(suffix: String) = {
    webResourceSettings ++ Seq(webResourcesBase <<= resourceManaged { f => file(f.toString)},
                               webResources ++= Map(suffix -> (s"$modelsURL/$suffix")),
                               resourceGenerators in Compile <+= resolveWebResources,
                               (managedResourceDirectories in Compile <+= webResourcesBase),
                               (managedResourceDirectories in Compile) ~= { d => println(d); d},
                               unmanagedClasspath in Runtime <+= (webResourcesBase) map { bd => Attributed.blank(bd) })
  }

  //
  // subprojects
  //

  lazy val allModels = Project("epic-all-models", file("."), settings = buildSettings) aggregate (epicModelCore, epicParserSpanEnglish) dependsOn (epicParserSpanEnglish, epicModelCore)
  //lazy val epicParserLexEnglish = Project("epic-parser-en-lex", file("parser/en/lex"), settings =  buildSettings ++ Seq (libraryDependencies ++= deps) ++ downloadModel("epic/parser/models/en/lex/model.ser.gz")) dependsOn (epicModelCore)
  lazy val epicParserSpanEnglish = Project("epic-parser-en-span", file("parser/en/span"), settings =  buildSettings ++ Seq (libraryDependencies ++= deps)  ++ downloadModel("epic/parser/models/en/span/model.ser.gz")) dependsOn (epicModelCore)
  lazy val epicModelCore = Project("epic-models-core", file("core"), settings =  buildSettings ++ Seq (libraryDependencies ++= deps))
}

