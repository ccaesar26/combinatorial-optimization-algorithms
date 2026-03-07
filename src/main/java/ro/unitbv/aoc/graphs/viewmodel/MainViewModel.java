package ro.unitbv.aoc.graphs.viewmodel;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import ro.unitbv.aoc.graphs.algorithms.GraphAlgo;
import ro.unitbv.aoc.graphs.business.GraphManager;
import ro.unitbv.aoc.graphs.model.Edge;
import ro.unitbv.aoc.graphs.model.Node;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class MainViewModel {

    private final GraphManager graphManager;

    private final ObservableList<Node> nodesList = FXCollections.observableArrayList();
    private final ObservableList<Edge> edgesList = FXCollections.observableArrayList();
    private final StringProperty statusText = new SimpleStringProperty("Ready");

    // Selection State
    private Node startNode = null; // The first node clicked when making an edge
    private final ObjectProperty<Edge> selectedEdge = new SimpleObjectProperty<>();

    public MainViewModel() {
        this.graphManager = new GraphManager();
    }

    // --- Commands ---

    public void addNodeCommand(double x, double y) {
        String id = "N" + (nodesList.size() + 1);
        graphManager.addNode(id, x, y);
        refreshData();
    }

    // CHANGED: Now accepts parameters for the new edge to be created
    public void onNodeClickCommand(Node clickedNode, double w, double c, double f, boolean directed) {
        if (startNode == null) {
            startNode = clickedNode;
            statusText.set("Start: " + clickedNode.id() + ". Click destination.");
        } else {
            if (!startNode.id().equals(clickedNode.id())) {
                // Create edge with the PASSED properties
                boolean ok = graphManager.addEdge(startNode.id(), clickedNode.id(), directed);
                if (ok) {
                    // Immediately update with the specific values (weights/capacity)
                    graphManager.updateEdge(startNode.id(), clickedNode.id(), w, c, f, directed);
                    statusText.set("Edge added: " + startNode.id() + " -> " + clickedNode.id());
                    refreshData();
                }
            }
            startNode = null; // Reset
        }
    }

    // --- ALGORITHMS ---

    // 1. Helper to fill the ComboBox in the View
    public List<String> getNodeIds() {
        return nodesList.stream().map(Node::id).collect(Collectors.toList());
    }

    // 2. The Command to run the Scala Algorithm
    public void runMaxFlowCommand(String sourceId, String sinkId) {
        if (sourceId == null || sinkId == null || sourceId.equals(sinkId)) {
            statusText.set("Error: Invalid Source/Sink selection.");
            return;
        }

        statusText.set("Running Max Flow (" + sourceId + " -> " + sinkId + ")...");

        // Call SCALA
        double maxFlow = GraphAlgo.runGenericMaxFlow(graphManager, sourceId, sinkId);

        refreshData(); // To show the flow values on edges
        statusText.set("Max Flow result: " + maxFlow);
    }

    public void selectEdgeCommand(Edge edge) {
        selectedEdge.set(edge);
        if (edge != null) {
            statusText.set("Selected: " + edge.sourceId() + " -> " + edge.targetId());
        }
    }

    public void updateSelectedEdgeCommand(double w, double c, double f, boolean isDirected) {
        Edge e = selectedEdge.get();
        if (e != null) {
            graphManager.updateEdge(e.sourceId(), e.targetId(), w, c, f, isDirected);
            refreshData();
            // We need to re-trigger selection to ensure list updates visually if needed
            // But simply refreshing data usually triggers the list update
            statusText.set("Edge updated.");
        }
    }

    public void clearCommand() {
        graphManager.clear();
        refreshData();
        startNode = null;
        selectedEdge.set(null);
    }

    // File I/O wrappers remain the same...
    public void saveGraphCommand(File f) {
        if (f != null) graphManager.saveGraph(f);
    }

    public void loadGraphCommand(File f) {
        if (f != null) {
            graphManager.loadGraph(f);
            refreshData();
        }
    }

    public void calculateStatsCommand() {
        statusText.set(graphManager.getGraphInfo());
    }

    public void deleteSelectedEdgeCommand() {
        Edge e = selectedEdge.get();
        if (e != null) {
            graphManager.removeEdge(e.sourceId(), e.targetId());
            selectedEdge.set(null); // Deselect
            statusText.set("Edge deleted.");
            refreshData();
        }
    }

    public void deleteNodeCommand(Node node) {
        if (node != null) {
            // Logic: If we are currently linking from this node, cancel the link
            if (startNode != null && startNode.id().equals(node.id())) {
                startNode = null;
            }

            graphManager.removeNode(node.id());
            statusText.set("Node " + node.id() + " deleted.");
            refreshData();
        }
    }

    private void refreshData() {
        nodesList.setAll(graphManager.getNodesJava());
        edgesList.setAll(graphManager.getEdgesJava());
    }

    public ObservableList<Node> getNodesList() {
        return nodesList;
    }

    public ObservableList<Edge> getEdgesList() {
        return edgesList;
    }

    public StringProperty statusTextProperty() {
        return statusText;
    }

    public ObjectProperty<Edge> selectedEdgeProperty() {
        return selectedEdge;
    }
}