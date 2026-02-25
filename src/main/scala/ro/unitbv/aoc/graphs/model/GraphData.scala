package ro.unitbv.aoc.graphs.model

case class Node(id: String, x: Double, y: Double)

// Default values provided for ease of use
case class Edge(
                 sourceId: String,
                 targetId: String,
                 var weight: Double = 1.0,
                 var capacity: Double = 0.0,
                 var flow: Double = 0.0,
                 var isDirected: Boolean = false
               ) {
  // Helper to format text for UI
  override def toString: String = {
    val arrow = if (isDirected) "->" else "--"
    f"$sourceId $arrow $targetId (W:$weight%.1f, C:$capacity%.1f)"
  }
}