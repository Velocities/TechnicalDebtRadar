package tdr.types

// This is a "unified" type for all code entities
enum EntityKind:
  case Class
  case Interface
  case Method
  // Note: This is parameters for a function or method, NOT the parameters of the class
  case Parameter
  case Field
  case Module

// This is just a Node in the graph: there is no edge relationship represented here,
// that's the 
final case class CodeEntity(
  id: String,
  name: String,
  file: String,
  // This is the type of the entity (e.g. Class, Interface, Method, Parameter, Field, Module)
  kind: EntityKind,
  // This is the metrics of the entity (e.g. LOC, CYCLO, etc.)
  metrics: EntityMetrics = EntityMetrics()
)

case class EntityMetrics(
  loc: Int = 0,
  complexity: Int = 0,
  dependencyCount: Int = 0,
  fieldCount: Int = 0,
  methodCount: Int = 0
)