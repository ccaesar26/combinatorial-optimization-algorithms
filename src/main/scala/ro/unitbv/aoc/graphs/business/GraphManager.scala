package ro.unitbv.aoc.graphs.business

import ro.unitbv.aoc.graphs.model.{Node, Edge}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import java.io.{PrintWriter, File}
import scala.io.Source

class GraphManager {

  private val nodes = mutable.Map[String, Node]()
  private val edges = mutable.ListBuffer[Edge]()

  // ... (previous add/update methods remain the same) ...

  def addNode(id: String, x: Double, y: Double): Boolean = {
    if (nodes.contains(id)) false else { nodes(id) = Node(id, x, y); true }
  }

  def addEdge(u: String, v: String, directed: Boolean = false): Boolean = {
    val exists = edges.exists(e => e.sourceId == u && e.targetId == v)
    if (nodes.contains(u) && nodes.contains(v) && !exists) {
      edges += Edge(u, v, isDirected = directed)
      true
    } else false
  }

  def updateEdge(sourceId: String, targetId: String, weight: Double, capacity: Double, flow: Double, directed: Boolean): Unit = {
    edges.find(e => e.sourceId == sourceId && e.targetId == targetId).foreach { e =>
      e.weight = weight
      e.capacity = capacity
      e.flow = flow
      e.isDirected = directed
    }
  }

  // --- NEW DELETE METHODS ---

  def removeEdge(sourceId: String, targetId: String): Unit = {
    // Remove edge from the buffer
    val idx = edges.indexWhere(e => e.sourceId == sourceId && e.targetId == targetId)
    if (idx >= 0) {
      edges.remove(idx)
    }
  }

  def removeNode(id: String): Unit = {
    // 1. Remove the node
    nodes.remove(id)

    // 2. Cascading delete: Remove any edge connected to this node
    // filterInPlace keeps elements where the condition is true
    edges.filterInPlace(e => e.sourceId != id && e.targetId != id)
  }

  def clear(): Unit = {
    nodes.clear()
    edges.clear()
  }

  // ... (Rest of file: Getters, Save, Load remain exactly the same) ...
  def getNodesJava: java.util.List[Node] = nodes.values.toList.asJava
  def getEdgesJava: java.util.List[Edge] = edges.toList.asJava
  def getGraphInfo: String = s"Nodes: ${nodes.size}, Edges: ${edges.size}"

  def saveGraph(file: File): Unit = {
    val pw = new PrintWriter(file)
    try {
      nodes.values.foreach { n => pw.println(s"NODE ${n.id} ${n.x} ${n.y}") }
      edges.foreach { e => pw.println(s"EDGE ${e.sourceId} ${e.targetId} ${e.weight} ${e.capacity} ${e.flow} ${e.isDirected}") }
    } finally { pw.close() }
  }

  def loadGraph(file: File): Unit = {
    clear()
    val source = Source.fromFile(file)
    try {
      for (line <- source.getLines()) {
        val parts = line.trim.split("\\s+")
        if (parts.nonEmpty) {
          parts(0) match {
            case "NODE" if parts.length >= 4 => addNode(parts(1), parts(2).toDouble, parts(3).toDouble)
            case "EDGE" if parts.length >= 3 =>
              val u = parts(1); val v = parts(2)
              val w = if (parts.length > 3) parts(3).toDouble else 1.0
              val c = if (parts.length > 4) parts(4).toDouble else 0.0
              val f = if (parts.length > 5) parts(5).toDouble else 0.0
              val d = if (parts.length > 6) parts(6).toBoolean else false
              edges += Edge(u, v, w, c, f, d)
            case _ => // ignore
          }
        }
      }
    } finally { source.close() }
  }
}