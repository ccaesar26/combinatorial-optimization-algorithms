package ro.unitbv.aoc.graphs.algorithms

import ro.unitbv.aoc.graphs.business.GraphManager
import ro.unitbv.aoc.graphs.model.{Edge, Node}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Map, Queue}
import scala.util.Random

object GraphAlgo {

  // Internal helper to represent a step in the Residual Graph
  // If isForward = true, we use remaining capacity (Capacity - Flow)
  // If isForward = false, we use existing flow (Flow) to push back
  case class ResidualStep(from: String, to: String, edge: Edge, isForward: Boolean)

  def runEdmondsKarp(manager: GraphManager, s: String, t: String): Double = {
    // 1. Initialization: f := 0 (Reset all flows to 0)
    // We access the mutable buffer directly from the manager
    // Note: We need to expose 'edges' in GraphManager or use a getter.
    // Assuming we pass the list of edges:
    val edges = manager.getEdgesJava // We will use the Java getter for simplicity or add a Scala one

    // Reset flow
    val edgesScala = edges.iterator()
    while (edgesScala.hasNext) {
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

  /**
   * Generic Ford-Fulkerson (GENERIC-FD) max-flow algorithm.
   *
   * Uses a randomized DFS to find augmenting paths in the residual graph,
   * as opposed to Edmonds-Karp which uses BFS.
   *
   * (1) f := f0 (initial flow = 0)
   * (2) Build residual graph G~(f)
   * (3) While G~(f) contains augmenting path:
   * (4)   Find augmenting path D~ (random DFS)
   * (5)   r(D~) := min{r(x,y) | (x,y) in D~}
   * (6)   Augment flow
   * (7)   Update G~(f)
   */
  def runGenericMaxFlow(manager: GraphManager, s: String, t: String): Double = {
    import scala.jdk.CollectionConverters._
    val allEdges = manager.getEdgesJava.asScala.toList

    // (1) f := f0 — reset all flows to 0
    allEdges.foreach(_.flow = 0.0)

    var totalFlow = 0.0
    var pathFound = true

    // (3) While G~(f) contains augmenting path
    while (pathFound) {
      // Find augmenting path using random DFS
      val path = findAugmentingPathRandomDFS(allEdges, s, t)

      path match {
        case Some(steps) =>
          // (5) r(D~) := min{r(x,y) | (x,y) in D~}
          var minResidual = Double.MaxValue
          for (step <- steps) {
            val residualCap = if (step.isForward) {
              step.edge.capacity - step.edge.flow
            } else {
              step.edge.flow
            }
            minResidual = Math.min(minResidual, residualCap)
          }

          // (6) Augment flow along the path
          for (step <- steps) {
            if (step.isForward) {
              step.edge.flow += minResidual
            } else {
              step.edge.flow -= minResidual
            }
          }

          totalFlow += minResidual

        case None =>
          pathFound = false
      }
    }

    totalFlow
  }

  /**
   * Find an augmenting path from s to t using randomized DFS on the residual graph.
   * Returns Some(list of ResidualStep) if a path is found, None otherwise.
   */
  private def findAugmentingPathRandomDFS(
                                           allEdges: List[Edge],
                                           s: String,
                                           t: String
                                         ): Option[List[ResidualStep]] = {

    val random = new Random()

    def dfs(current: String, visited: mutable.Set[String], path: List[ResidualStep]): Option[List[ResidualStep]] = {
      if (current == t) return Some(path)

      // Collect all neighbours with positive residual capacity
      val neighbours = ListBuffer[(String, ResidualStep)]()

      for (edge <- allEdges) {
        // Forward edge: current -> targetId
        if (edge.sourceId == current && !visited.contains(edge.targetId)) {
          val residual = edge.capacity - edge.flow
          if (residual > 0) {
            neighbours += ((edge.targetId, ResidualStep(current, edge.targetId, edge, isForward = true)))
          }
        }
        // Backward edge: current -> sourceId (push back flow)
        if (edge.targetId == current && !visited.contains(edge.sourceId)) {
          val residual = edge.flow
          if (residual > 0) {
            neighbours += ((edge.sourceId, ResidualStep(current, edge.sourceId, edge, isForward = false)))
          }
        }
      }

      // Shuffle for random selection
      val shuffled = random.shuffle(neighbours)

      for ((neighbour, step) <- shuffled) {
        visited.add(neighbour)
        val result = dfs(neighbour, visited, path :+ step)
        if (result.isDefined) return result
        visited.remove(neighbour) // Backtrack
      }

      None
    }

    val visited = mutable.Set[String](s)
    dfs(s, visited, List.empty)
  }

  /**
   * 5.4.2 Algoritmul Ford-Fulkerson de etichetare (PROGRAM FFE)
   * 5.4.2 Algoritmul Ford-Fulkerson de etichetare (PROGRAM FFE)
   *
   * Uses a random list-based frontier exploration to find augmenting paths,
   * matching the classic Ford-Fulkerson labeling procedure where the next
   * node to expand is chosen arbitrarily (here: at random) from the frontier.
   */
  def runFordFulkerson(manager: GraphManager, s: String, t: String): Double = {
    import scala.jdk.CollectionConverters._
    val allEdges = manager.getEdgesJava.asScala.toList

    // (3) f := f0; (We start with flow 0)
    allEdges.foreach(_.flow = 0.0)

    val random = new Random()
    var totalFlow = 0.0
    var pathExists = true

    // (6) WHILE p(t) != 0 DO
    while (pathExists) {

      // (8) FOR y in N DO p(y) := 0
      val prev = mutable.Map[String, ResidualStep]()

      // (9) V := {s}; p(s) := sentinel
      // We use a ListBuffer as a random-access frontier (not a Queue!)
      val frontier = ListBuffer[String](s)
      // Mark source as visited with a sentinel entry
      prev(s) = ResidualStep(s, s, null, isForward = true)

      // (10) WHILE V != empty AND p(t) == 0 DO
      while (frontier.nonEmpty && !prev.contains(t)) {

        // Pick a random node from the frontier (Ford-Fulkerson: arbitrary choice)
        val idx = random.nextInt(frontier.size)
        val x = frontier.remove(idx)

        // (13) FOR (x,y) din A_tilde DO (Explore residual graph)
        for (edge <- allEdges) {

          // Case A: Forward Edge (Capacity - Flow > 0)
          if (edge.sourceId == x && !prev.contains(edge.targetId)) {
            val residualCap = edge.capacity - edge.flow
            if (residualCap > 0) {
              prev(edge.targetId) = ResidualStep(x, edge.targetId, edge, isForward = true)
              frontier += edge.targetId
            }
          }

          // Case B: Backward Edge (Flow > 0)
          if (edge.targetId == x && !prev.contains(edge.sourceId)) {
            val residualCap = edge.flow
            if (residualCap > 0) {
              prev(edge.sourceId) = ResidualStep(x, edge.sourceId, edge, isForward = false)
              frontier += edge.sourceId
            }
          }
        }
      }

      // (17) IF p(t) != 0 THEN MĂRIRE
      if (prev.contains(t)) {
        // --- PROCEDURA MĂRIRE ---
        var curr = t
        var r_D = Double.MaxValue
        val path = ListBuffer[ResidualStep]()

        // (3) Reconstruct augmenting path from prev pointers
        // (4) r(D) = min { r(x,y) }
        while (curr != s) {
          val step = prev(curr)
          path.prepend(step)

          val residualCap = if (step.isForward) step.edge.capacity - step.edge.flow else step.edge.flow
          r_D = Math.min(r_D, residualCap)

          curr = step.from
        }

        // (5) Augment flow along D
        for (step <- path) {
          if (step.isForward) {
            step.edge.flow += r_D
          } else {
            step.edge.flow -= r_D
          }
        }

        totalFlow += r_D
      } else {
        pathExists = false
      }
    }

    totalFlow
  }
}