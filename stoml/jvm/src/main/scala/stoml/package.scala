/** Implicitly provides function to convert [[java.lang.String]]
  * into [[java.util.Date]], under a proper JVM platform.
  */
package object stoml {
  import java.text.SimpleDateFormat

  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  implicit val dateConverter: (String => java.util.Date) = { (s: String) =>
    formatter.parse(s)
  }
}