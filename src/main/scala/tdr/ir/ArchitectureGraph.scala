package tdr.ir

/**
 * The Architecture Intermediate Representation.
 *
 * This graph is language-independent.
 *
 * Nodes:
 *   CodeEntity objects representing files, classes, methods, etc.
 *
 * Edges:
 *   CodeEdge objects representing relationships between entities.
 *
 * The edges collection is the source of truth.
 *
 * The indexes are derived lookup structures used to make queries fast:
 *
 * outgoingIndex:
 *   "What does this entity depend on?"
 *
 * incomingIndex:
 *   "What entities depend on this one?"
 *
 * The indexes do NOT create duplicate relationships. They only point to
 * existing CodeEdge instances so queries do not need to scan the entire edge set.
 */
final case class ArchitectureGraph(
    nodes: Map[EntityId, CodeEntity],
    edges: Set[CodeEdge],
    outgoingIndex: Map[EntityId, Set[CodeEdge]],
    incomingIndex: Map[EntityId, Set[CodeEdge]]
):

  /**
   * Find an entity by its ID.
   */
  def entity(id: EntityId): Option[CodeEntity] =
    nodes.get(id)


  /**
   * All outgoing relationships from an entity.
   */
  def outgoing(id: EntityId): Set[CodeEdge] =
    outgoingIndex.getOrElse(id, Set.empty)


  /**
   * All incoming relationships to an entity.
   */
  def incoming(id: EntityId): Set[CodeEdge] =
    incomingIndex.getOrElse(id, Set.empty)


  /**
   * Entities this node depends on.
   *
   * Example:
   * UserService -> DatabaseService
   */
  def dependencies(id: EntityId): Set[EntityId] =
    outgoing(id)
      .filter(_.relation == RelationType.DependsOn)
      .map(_.to)


  /**
   * Entities that depend on this node.
   *
   * Example:
   * Five services depend on DatabaseService
   */
  def dependents(id: EntityId): Set[EntityId] =
    incoming(id)
      .filter(_.relation == RelationType.DependsOn)
      .map(_.from)


  /**
   * Basic coupling metric.
   *
   * Number of unique entities connected through dependency relationships.
   */
  def coupling(id: EntityId): Int =
    (dependencies(id) ++ dependents(id)).size



object ArchitectureGraph:

  /**
   * Creates an empty graph.
   *
   * Useful for tests or incremental building.
   */
  val empty: ArchitectureGraph =
    ArchitectureGraph(
      nodes = Map.empty,
      edges = Set.empty,
      outgoingIndex = Map.empty,
      incomingIndex = Map.empty
    )


  /**
   * Builds indexes from the canonical edge collection.
   *
   * Edges are the truth.
   * Indexes are generated from edges.
   */
  def build(
      nodes: Map[EntityId, CodeEntity],
      edges: Set[CodeEdge]
  ): ArchitectureGraph =

    ArchitectureGraph(
      nodes = nodes,
      edges = edges,
      outgoingIndex = edges.groupBy(_.from),
      incomingIndex = edges.groupBy(_.to)
    )