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

  val epic = "org.scalanlp" %% "epic" % "0.1-SNAPSHOT"

  val deps = Seq(epic)




  //
  // subprojects
  //

  lazy val allModels = Project("epic-all-models", file("."), settings = buildSettings) aggregate (epicModelCore, epicParserSpanEnglish) dependsOn (epicParserSpanEnglish, epicModelCore)
  //lazy val epicParserLexEnglish = Project("epic-parser-en-lex", file("parser/en/lex"), settings =  buildSettings ++ Seq (libraryDependencies ++= deps) ++ downloadModel("epic/parser/models/en/lex/model.ser.gz")) dependsOn (epicModelCore)
  lazy val epicParserSpanEnglish = Project("epic-parser-en-span",
    file("parser/en/span"),
    settings =  buildSettings
      ++ Seq (libraryDependencies ++= deps)
      ++ ModelGenerator.parserLoader("en", "Span")) dependsOn (epicModelCore)
  lazy val epicModelCore = Project("epic-models-core", file("core"), settings =  buildSettings ++ Seq (libraryDependencies ++= deps))
}

object ModelGenerator {
  import WebResources.{buildSettings => _, _}


  val modelsURL = "http://www.scalanlp.org/resources/"

  def downloadModel(suffix: String) = {
    webResourceSettings ++ Seq(webResourcesBase <<= resourceManaged { f => file(f.toString)},
      webResources ++= Map(suffix -> (s"$modelsURL/$suffix")),
      resourceGenerators in Compile <+= resolveWebResources,
      (managedResourceDirectories in Compile <+= webResourcesBase),
      (managedResourceDirectories in Compile) ~= { d => println(d); d},
      unmanagedClasspath in Runtime <+= (webResourcesBase) map { bd => Attributed.blank(bd) })
  }

  def generateModelLoader(lang2letter: String, model: String, system: String, systemClass: String, imports: String*) = {
    val locale = new java.util.Locale(lang2letter)
    val lang = locale.getDisplayLanguage(java.util.Locale.US)
    val genSource: Def.Setting[Seq[Task[Seq[File]]]] = sourceGenerators in Compile <+= sourceManaged in Compile map { case srcPath =>
      val loaderPath = new File(srcPath, s"epic/${system.toLowerCase}/models/${lang2letter}/${model.toLowerCase}/${lang}${model}${system}.scala")
      val loaderSource = s"""
    package epic.${system.toLowerCase}.models.${lang2letter}.${model.toLowerCase}

    import epic.models._
    ${imports.mkString("\n")}


    object ${lang}${model}${system} extends epic.models.ClassPathModelLoader[${systemClass}] with ${system}ModelLoader with LanguageSpecific {
        class Loader() extends DelegatingLoader(this) with ${system}ModelLoader

        def language = "$lang2letter"
    }
    """

      IO.write(loaderPath, loaderSource)
        Seq(loaderPath)
    }


    val genRes: Def.Setting[Seq[Task[Seq[File]]]] = resourceGenerators in Compile <+= (resourceManaged in Compile) map { case  resPath =>

      val metainfPath =new File(resPath.getParentFile, s"META-INF/services/epic.models.${system}ModelLoader")
      val metainfSource = s"epic.${system.toLowerCase}.models.${lang2letter}.${model.toLowerCase}.${lang}${model}${system}$$Loader"
      IO.write(metainfPath, metainfSource)

      Seq(metainfPath)
    }

    val modelPath = s"epic/${system.toLowerCase}/models/${lang2letter}/${model.toLowerCase}/model.ser.gz"
    Seq(genSource, genRes) ++ downloadModel(modelPath)
  }

  def parserLoader(lang2letter: String, model: String = "Span") = {
    generateModelLoader(lang2letter, model, "Parser", "epic.parser.Parser[AnnotatedLabel, String]", "import epic.trees._")
  }


  def nerLoader(lang2letter: String, model: String = "Conll")(srcPath: File, resourcePath: File) = {
    generateModelLoader(lang2letter, model, "NER", "epic.sequences.SemiCRF[String, String]", "import epic.sequences._")
  }

}
