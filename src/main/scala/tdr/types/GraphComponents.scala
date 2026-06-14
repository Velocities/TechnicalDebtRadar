// This file handles the components of the graph: nodes and edges
package tdr.types

import tdr.ir.EntityId

enum RelationType:
  case Contains
  case Calls
  case DependsOn
  case Implements
  case Extends
  case Imports

final case class CodeEdge(
  from: EntityId,
  to: EntityId,
  relation: RelationType
)