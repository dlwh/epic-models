package epic.models

trait LanguageSpecific[+T] extends ModelLoader[T] {

  def language: String

  def capabilities():Array[String] = Array(s"language:$language")
}


trait EnglishModel[+T] extends LanguageSpecific[T] {
  def language = "en"
}