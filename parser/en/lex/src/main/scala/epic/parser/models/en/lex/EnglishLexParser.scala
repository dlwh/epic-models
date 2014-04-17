package epic.parser.models.en.lex
import epic.trees.AnnotatedLabel
import epic.models.{DelegatingLoader, ParserModelLoader, EnglishModel}

object EnglishLexParser extends epic.models.ClassPathModelLoader[epic.parser.Parser[AnnotatedLabel, String]] with ParserModelLoader with EnglishModel[epic.parser.Parser[AnnotatedLabel, String]] {
  class Loader() extends DelegatingLoader(this) with ParserModelLoader
}

