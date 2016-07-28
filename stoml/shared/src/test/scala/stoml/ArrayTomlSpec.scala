package stoml

import org.scalacheck.Gen
import org.scalatest.prop._
import org.scalatest.{Matchers, PropSpec}

import scala.language.postfixOps

trait ArrayTomlGen {
  this: TomlSymbol
    with StringTomlGen
    with NumbersTomlGen =>

  val openChars = List("[", "[\n")
  val seps = List(",\n", ",")
  val maybeTrailingComma = List("", ",")
  val closeChars = List("]", "\n]")

  def arrayFormat(s: Seq[_], fs: (String, String, String, String)): String =
    fs._1 + (s mkString fs._2) + fs._3 + fs._4

  import Gen.{nonEmptyListOf, oneOf}

  def arrayGen = for {
    ts <- oneOf(validStrGen, validDoubleGen, validLongGen)
    elems <- nonEmptyListOf(ts)
    c1 <- oneOf(openChars)
    ss <- oneOf(seps)
    c2 <- oneOf(closeChars)
  } yield arrayFormat(elems, (c1, ss, "", c2))

  def multilineArrayGen = for {
    ts <- oneOf(validStrGen, validDoubleGen, validLongGen)
    elems <- nonEmptyListOf(ts)
    tc <- oneOf(maybeTrailingComma)
  } yield arrayFormat(elems, ("[\n", ",\n", tc, "\n]"))
}

class ArrayTomlSpec extends PropSpec 
    with PropertyChecks with Matchers
    with NumbersTomlGen with StringTomlGen
    with ArrayTomlGen with TomlParser
    with TestParserUtil {

  property("parse arrays") {
    forAll(arrayGen) {
      s: String =>
        shouldBeSuccess(elem.parse(s))
    }
  }

  property("parse multiline arrays with possible trailing comma") {
    forAll(multilineArrayGen) {
      s: String =>
        shouldBeSuccess(elem.parse(s))
    }
  }
}
