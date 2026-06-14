// This file handles the components of the graph: nodes and edges
package tdr.types

enum RelationType:
  case Contains
  case Calls
  case DependsOn
  case Implements
  case Extends
  case Imports

final case class CodeEdge(
  from: String,
  to: String,
  relation: RelationType
)