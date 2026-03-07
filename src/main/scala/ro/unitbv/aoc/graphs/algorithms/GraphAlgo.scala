package ro.unitbv.aoc.graphs.algorithms

import ro.unitbv.aoc.graphs.business.GraphManager
import ro.unitbv.aoc.graphs.model.{Edge, Node}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Queue, Map}

object GraphAlgo {

  // Internal helper to represent a step in the Residual Graph
  // If isForward = true, we use remaining capacity (Capacity - Flow)
  // If isForward = false, we use existing flow (Flow) to push back
  case class ResidualStep(from: String, to: String, edge: Edge, isForward: Boolean)

  def runGenericMaxFlow(manager: GraphManager, s: String, t: String): Double = {
    // 1. Initialization: f := 0 (Reset all flows to 0)
    // We access the mutable buffer directly from the manager
    // Note: We need to expose 'edges' in GraphManager or use a getter.
    // Assuming we pass the list of edges:
    val edges = manager.getEdgesJava // We will use the Java getter for simplicity or add a Scala one

    // Reset flow
    val edgesScala = edges.iterator()
    while(edgesScala.hasNext) {
      edgesScala.next().flow = 0.0
    }

    var totalFlow = 0.0
    var pathFound = true

    // 2. WHILE G(f) contains a DMF DO
    while (pathFound) {
      // Find a path in the Residual Graph using BFS
      val parentMap = bfs(manager, s, t)

      if (parentMap.contains(t)) {
        // Reconstruct path from t back to s
        var curr = t
        val path = ListBuffer[ResidualStep]()
        var minCapacity = Double.MaxValue

        while (curr != s) {
          val step = parentMap(curr)
          path.prepend(step)

          // Calculate r(D) = min { r(x,y) }
          val residualCap = if (step.isForward) {
            step.edge.capacity - step.edge.flow
          } else {
            step.edge.flow // Backward edge capacity is the current flow
          }

          minCapacity = Math.min(minCapacity, residualCap)
          curr = step.from
        }

        // 3. Augment flow: f(x,y) = f(x,y) + r(D) (or - r(D) for backward)
        for (step <- path) {
          if (step.isForward) {
            step.edge.flow += minCapacity
          } else {
            step.edge.flow -= minCapacity
          }
        }

        // Update total flow
        totalFlow += minCapacity
      } else {
        pathFound = false
      }
    }

    totalFlow
  }

  // Helper BFS to find augmenting path in Residual Graph
  private def bfs(manager: GraphManager, s: String, t: String): Map[String, ResidualStep] = {
    val parents = Map[String, ResidualStep]()
    val visited = mutable.Set[String](s)
    val queue = Queue[String](s)

    // Convert Java List to Scala for easier traversal
    import scala.jdk.CollectionConverters._
    val allEdges = manager.getEdgesJava.asScala.toList

    while (queue.nonEmpty) {
      val u = queue.dequeue()

      if (u == t) return parents // Found path

      // Explore neighbors in Residual Graph
      for (edge <- allEdges) {
        // 1. Forward Edge (u -> v): Valid if Capacity > Flow
        if (edge.sourceId == u && !visited.contains(edge.targetId)) {
          val residual = edge.capacity - edge.flow
          if (residual > 0) {
            visited.add(edge.targetId)
            parents(edge.targetId) = ResidualStep(u, edge.targetId, edge, isForward = true)
            queue.enqueue(edge.targetId)
          }
        }

        // 2. Backward Edge (v -> u): Valid if Flow > 0
        // (We can "push back" flow)
        if (edge.targetId == u && !visited.contains(edge.sourceId)) {
          val residual = edge.flow
          if (residual > 0) {
            visited.add(edge.sourceId)
            parents(edge.sourceId) = ResidualStep(u, edge.sourceId, edge, isForward = false)
            queue.enqueue(edge.sourceId)
          }
        }
      }
    }
    parents
  }
}