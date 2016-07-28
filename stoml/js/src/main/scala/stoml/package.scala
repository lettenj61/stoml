/** Implicitly provides function to convert [[java.lang.String]]
  * into [[java.util.Date]], under a Scala.js environment.
  */
package object stoml {
  import scala.scalajs.js

  implicit val dateConverter: (String => java.util.Date) = { (s: String) =>
    new java.util.Date(js.Date.parse(s).toLong)
  }
}