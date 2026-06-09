package tdr.types

/** A source file discovered on disk, with its lines of code and the raw import
  * targets extracted from it.
  */
final case class ScannedFile(
    // Relative path to the root of the repository
    relativePath: String,
    // Lines of code
    loc: Int,
    // Import targets
    imports: Set[String]
)
