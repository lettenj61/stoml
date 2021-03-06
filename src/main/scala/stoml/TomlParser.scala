package stoml

import scala.language.{implicitConversions, postfixOps}
import java.util.{Date => JDate}
import java.text.SimpleDateFormat

object Toml extends TomlSymbol {
  case class NamedFunction[T, V](f: T => V, name: String)
    extends (T => V) {
      def apply(t: T) = f(t)
      override def toString() = name
  }

  sealed trait Elem extends Any { def v: Any }
  sealed trait Node extends Any with Elem

  sealed trait Bool extends Elem
  case object True extends Bool {def v = true}
  case object False extends Bool {def v = false}

  case class Str(v: String) extends AnyVal with Elem
  object Str {
    def dequoteStr(s: String, q: String) =
      s.stripPrefix(q).stripSuffix(q)
    def cleanStr(s: String): String =
      dequoteStr(dequoteStr(s, SingleQuote), DoubleQuote)
    def cleanedApply(s: String): Str = Str(cleanStr(s))
  }

  case class Integer(v: Long) extends AnyVal with Elem
  case class Real(v: Double) extends AnyVal with Elem
  case class Arr(v: Seq[Elem]) extends AnyVal with Elem
  case class Date(v: JDate) extends AnyVal with Elem
  case class Pair(v: (String, Elem)) extends AnyVal with Node

  type TableName = Vector[String]
  case class Table(v: (TableName, Map[String, Elem])) extends Node
  object Table {
    def apply(ls: TableName, ps: Seq[Pair]): Table =
      Table(ls.toVector -> (ps map (Pair.unapply(_).get) toMap))
  }
}

trait ParserUtil {
  this: TomlSymbol =>
  import Toml.NamedFunction
  val Whitespace = NamedFunction(WSChars.contains(_: Char), "Whitespace")
  val Digits = NamedFunction('0' to '9' contains (_: Char), "Digits")
  val Letters = NamedFunction((('a' to 'z') ++ ('A' to 'Z')).contains, "Letters")
  val UntilNewline = NamedFunction(!NLChars._1.contains(_: Char), "UntilNewline")
}

trait TomlSymbol {
  val SingleQuote = "\'"
  val DoubleQuote = "\""
  val Quotes = SingleQuote + DoubleQuote
  val Braces = ("[", "]")
  val Dashes = "-_"
  val NLChars = ("\r\n", "\n")
  val WSChars = " \t"
  val CommentSymbol = "#"
}

trait TomlParser extends ParserUtil with TomlSymbol {
  import fastparse.all._
  import Toml._

  val newline = P(StringIn(NLChars._1, NLChars._2))
  val charsChunk = P(CharsWhile(UntilNewline))
  val comment: P0 = P { CommentSymbol ~ charsChunk.rep ~ &(newline | End) }
  val WS0: P0 = P { CharsWhile(Whitespace) }
  val WS: P0 = P { NoCut(NoTrace((WS0 | comment | newline).rep.?)) }

  val letters = P { CharsWhile(Letters) }
  val digit = P { CharIn('0' to '9') }
  val digits = P { CharsWhile(Digits) }

  val literalChars = NamedFunction(!SingleQuote.contains(_: Char), "LitStr")
  val basicChars = NamedFunction(!DoubleQuote.contains(_: Char), "BasicStr")
  val unescapedChars = P { CharsWhile(literalChars) }
  val escapedChars = P { CharsWhile(basicChars) | "\\\""}

  val basicStr: Parser[Str] =
    P { DoubleQuote ~/ escapedChars.rep.! ~ DoubleQuote } map Str.cleanedApply
  val literalStr: Parser[Str] =
    P { SingleQuote ~/ unescapedChars.rep.! ~ SingleQuote } map Str.cleanedApply
  val string: Parser[Str] = P { basicStr | literalStr }

  def rmUnderscore(s: String) = s.replace("_", "")
  val +- = P { CharIn("+-") }
  val integral = P { digits.rep(min=1, sep="_") }
  val fractional = P { "." ~ integral }
  val exponent = P { CharIn("eE") ~ +-.? ~ integral }
  val integer: Parser[Integer] =
    P { +-.? ~ integral }.! map (s => Integer(rmUnderscore(s).toLong))
  val double: Parser[Real] =
    P { +-.? ~ integral ~ (fractional | exponent) }.! map {
      s => Real(rmUnderscore(s).toDouble)
    }

  val `true` = P { "true" } map (_ => True)
  val `false` = P { "false" } map (_ => False)
  val boolean: Parser[Bool] = P { `true` | `false` }

  lazy val date: Parser[Date] =
    rfc3339.opaque("<valid-date-rfc3339>").map { t =>
      /* Even though this extra parsing is not necessary,
       * it is done just for simplicity, avoiding the use
       * of `java.util.Calendar` instances. */
      Date(formatter.parse(t))
    }

  private val formatter = 
    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  def twice[T](p: Parser[T]) = p ~ p
  def fourTimes[T](p: Parser[T]) = twice(p) ~ twice(p)
  val rfc3339: Parser[String] =
    P { fourTimes(digit) ~ "-" ~ twice(digit) ~ "-" ~ 
        twice(digit) ~ "T" ~ twice(digit) ~ ":" ~
        twice(digit) ~ ":" ~ twice(digit) ~ ("." ~
        digit.rep(min=3, max=3)).? ~ "Z".? }.!

  val dashes = P { CharIn(Dashes) }
  val bareKey = P { (letters | digits | dashes).rep(min=1) }.!
  val validKey: Parser[String] = P { bareKey | NoCut(basicStr) }.!
  lazy val pair: Parser[Pair] =
    P { validKey ~ WS0.? ~ "=" ~ WS0.? ~ elem } map Pair
  lazy val array: Parser[Arr] =
    P { "[" ~ WS ~ elem.rep(sep=WS0.? ~ "," ~/ WS) ~ WS ~ "]" } map Arr

  val tableIds: Parser[Seq[String]] =
    P { validKey.rep(min=1, sep=WS0.? ~ "." ~ WS0.?) }
  val tableDef: Parser[Seq[String]] =
    P { "[" ~ WS0.? ~ tableIds ~ WS0.? ~ "]" }
  val table: Parser[Table] =
    P { WS ~ tableDef ~ WS ~ pair.rep(sep=WS) } map {
      t => Table(t._1.toVector.map(Str.cleanStr), t._2)
    }

  lazy val elem: Parser[Elem] = P {
    WS ~ (string | boolean | double | integer | array | date) ~ WS
  }

  lazy val node: Parser[_ <: Node] = P { WS ~ (pair | table) ~ WS }
  lazy val nodes: Parser[Seq[Node]] = P { node.rep(min=1, sep=WS) ~ End }
}

trait TomlParserApi extends TomlParser {
  import stoml.Toml.{Node, Table, Pair}

  type Key = Vector[String]

  case class TomlContent(c: Map[Key, Node]) {
    def lookup(k: Key): Option[Node] = c.get(k)
  }

  object TomlContent {
    def apply(s: Seq[Node]): TomlContent = TomlContent {
        s.foldLeft(Map.empty[Key, Node])((m, e) => e match {
          case t: Table => m + (t.v._1 -> t)
          case p: Pair => m + (Vector(p.v._1) -> p)
        })
      }
  }

  import fastparse.all._
  import fastparse.core.Parsed
  def toToml(s: String): Parsed[TomlContent] =
    (nodes map TomlContent.apply).parse(s)
}

object TomlParserApi extends TomlParserApi
