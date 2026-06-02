package tdr.analysis

import tdr.ir.ArchitectureGraph
import scala.collection.mutable

/** Detects circular dependencies in the architecture graph.
  *
  * Uses Tarjan's algorithm to find strongly connected components; any SCC with
  * more than one node (or a single node with a self-edge) is a dependency
  * cycle.
  */
object CycleDetector:

  def findCycles(graph: ArchitectureGraph): List[List[String]] =
    stronglyConnectedComponents(graph).filter { scc =>
      scc.size > 1 || (scc.size == 1 && graph.dependencies(scc.head).contains(scc.head))
    }

  private[analysis] def stronglyConnectedComponents(
      graph: ArchitectureGraph
  ): List[List[String]] =
    var counter = 0
    val index = mutable.Map[String, Int]()
    val low = mutable.Map[String, Int]()
    val onStack = mutable.Set[String]()
    val stack = mutable.Stack[String]()
    val components = mutable.ListBuffer[List[String]]()

    def strongConnect(v: String): Unit =
      index(v) = counter
      low(v) = counter
      counter += 1
      stack.push(v)
      onStack += v

      for w <- graph.dependencies(v) if graph.nodes.contains(w) do
        if !index.contains(w) then
          strongConnect(w)
          low(v) = math.min(low(v), low(w))
        else if onStack(w) then low(v) = math.min(low(v), index(w))

      if low(v) == index(v) then
        val component = mutable.ListBuffer[String]()
        var w = ""
        while
          w = stack.pop()
          onStack -= w
          component += w
          w != v
        do ()
        components += component.toList

    for v <- graph.nodes.keys if !index.contains(v) do strongConnect(v)
    components.toList
