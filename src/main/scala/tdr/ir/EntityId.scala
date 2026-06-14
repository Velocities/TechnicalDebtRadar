package tdr.ir

/**
 * Strongly-typed identifier for any node in the architecture graph.
 *
 * This prevents accidental mixing of raw Strings (file paths, names, etc.)
 * with stable graph identity values.
 */
final case class EntityId(value: String) derives CanEqual:

  override def toString: String = value