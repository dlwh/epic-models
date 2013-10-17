package epic.parser.models.en.lex
import java.io._
import java.util.zip._
import epic.trees.AnnotatedLabel

object EnglishLexParser extends epic.models.ClassPathModelLoader[epic.parser.Parser[AnnotatedLabel, String]];
