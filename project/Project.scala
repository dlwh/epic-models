import sbt.Keys._
import sbt._


object BuildSettings {
  val buildOrganization = "org.scalanlp"
  val buildScalaVersion = "2.11.4"
  val buildVersion = "2015.2.19"


  val buildSettings = Defaults.defaultSettings ++ Seq (
    version := buildVersion,
    organization := buildOrganization,
    scalaVersion := buildScalaVersion,
    resolvers ++= Seq(
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "FormReturn" at "http://maven.formreturn.com/repository/"
    ),
    crossScalaVersions := Seq("2.11.4", "2.10.4"),
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
      <url>git@github.com:dlwh/epic-models.git</url>
      <connection>scm:git:git@github.com:dlwh/epic-models.git</connection>
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

  val epic = "org.scalanlp" %% "epic" % "0.3.1"

  val deps = Seq(epic)


  val parserLanguages = Seq("de", "en", "eu", "fr", "hu", "ko", "pl", "sv")
  val posLanguages =   Seq("de", "en", "eu", "fr", "hu",       "pl", "sv")
  val nerLanguages =    Seq("en")

  lazy val parserModels = parserLanguages.map( lang =>
    Project(s"epic-parser-$lang-span",
    file(s"parser/$lang/span"),
    settings =  buildSettings
      ++ Seq (libraryDependencies ++= deps)
      ++ ModelGenerator.parserLoader(lang, "Span")) 

  )


  lazy val posModels = posLanguages.map( lang =>
    Project(s"epic-pos-$lang",
    file(s"pos/$lang"),
    settings =  buildSettings
      ++ Seq (libraryDependencies ++= deps)
      ++ ModelGenerator.posLoader(lang, ""))

  )

  lazy val nerModels = nerLanguages.map( lang =>
    Project(s"epic-ner-$lang-conll",
    file(s"ner/$lang/conll"),
    settings =  buildSettings
      ++ Seq (libraryDependencies ++= deps)
      ++ ModelGenerator.nerLoader(lang, "Conll"))

  )


  //
  // subprojects
  //

  lazy val allModels = {
    val p = Project("epic-all-models", file("."), settings = buildSettings).aggregate ( allProjectReferences:_*)
    // stupid implicits
    allProjectReferences.foldLeft(p)(_.dependsOn(_))
  }

  lazy val allProjects = parserModels ++ posModels ++ nerModels
  lazy val allProjectReferences = allProjects.map(Project.projectToRef)


  lazy val englishModels = {
    val englishProjects = allProjects.filter(p => p.id.contains("-en-") || p.id.endsWith("-en")).map(Project.projectToRef)
    val p = Project("english", file("english"), settings = buildSettings).aggregate ( englishProjects:_*)
    (englishProjects).foldLeft(p)(_.dependsOn(_))
  }

  override def projects: Seq[Project] = allProjects :+ englishModels :+  allModels

}

object ModelGenerator {

  val modelsURL = "http://www.scalanlp.org/resources/"



  def generateModelLoader(lang2letter: String, model: String, kind: String, system: String, systemClass: String, imports: String*) = {
    val locale = new java.util.Locale(lang2letter)
    val lang = locale.getDisplayLanguage(java.util.Locale.US)
    val pack = s"epic.${kind.toLowerCase}.models.${lang2letter}.${if(model.nonEmpty) model.toLowerCase else system.toLowerCase}"
    val genSource: Def.Setting[Seq[Task[Seq[File]]]] = sourceGenerators in Compile <+= sourceManaged in Compile map { case srcPath =>
      val loaderPath = new File(srcPath, s"epic/${kind.toLowerCase}/models/${lang2letter}/${model.toLowerCase}/${lang}${model}${system}.scala")
      val loaderSource = s"""
      package $pack

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


    val genRes: Def.Setting[Seq[Task[Seq[File]]]] = resourceGenerators in Compile <+= (resourceManaged in Compile, streams, scalaVersion in Compile) map { case  (resPath, s, version) =>

      val metainfPath =new File(resPath, s"META-INF/services/epic.models.${system}ModelLoader")
      val metainfSource = s"$pack.${lang}${model}${system}$$Loader"
      IO.write(metainfPath, metainfSource)

      val modelPath = s"${pack.replaceAll("[.]","/")}/model.ser.gz"
      val remoteFile = new URL(s"$modelsURL/${version.dropRight(2)}/$modelPath")
      val localFile = new File(resPath, modelPath)
      if(!localFile.exists) {
        s.log.info(s"Downloading... $remoteFile $localFile")
        IO.download(remoteFile, localFile)
      }

      Seq(metainfPath, localFile)
//      Seq.empty[File]
    }

    Seq(genSource, genRes)
  }

  def parserLoader(lang2letter: String, model: String = "Span") = {
    generateModelLoader(lang2letter, model, "Parser", "Parser", "epic.parser.Parser[AnnotatedLabel, String]", "import epic.trees._")
  }


  def nerLoader(lang2letter: String, model: String = "Conll") = {
    generateModelLoader(lang2letter, model, "Sequences", "Ner", "epic.sequences.SemiCRF[Any, String]", "import epic.sequences._")
  }

  
  def posLoader(lang2letter: String, model: String = "") = {
    generateModelLoader(lang2letter, model, "Sequences", "PosTag", "epic.sequences.CRF[AnnotatedLabel, String]", "import epic.sequences._", "import epic.trees._")
  }

}
