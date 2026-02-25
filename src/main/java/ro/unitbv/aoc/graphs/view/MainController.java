package ro.unitbv.aoc.graphs.view;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import ro.unitbv.aoc.graphs.model.Edge;
import ro.unitbv.aoc.graphs.model.Node;
import ro.unitbv.aoc.graphs.viewmodel.MainViewModel;

import java.io.File;

public class MainController {

    @FXML private Pane drawingPane;
    @FXML private Label lblStatus;

    // Sidebar Controls
    @FXML private ListView<Edge> edgeListView;
    @FXML private TextField txtWeight;
    @FXML private TextField txtCapacity;
    @FXML private TextField txtFlow;
    @FXML private CheckBox chkDirected;
    @FXML private Button btnUpdate;
    @FXML private Button btnDeleteEdge;
    @FXML private Label lblHeader;
    @FXML private Label lblSubHeader;

    private MainViewModel viewModel;

    @FXML
    public void initialize() {
        viewModel = new MainViewModel();
        chkDirected.setSelected(true);
        lblStatus.textProperty().bind(viewModel.statusTextProperty());

        // 1. Bind Data Source to ListView
        edgeListView.setItems(viewModel.getEdgesList());

        // Custom Cell Factory to display edge info nicely in the list
        edgeListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Edge item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // Using the toString helper we defined in Scala
                    setText(item.toString());
                }
            }
        });

        // 2. Handle List Selection
        edgeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.selectEdgeCommand(newVal); // Tell ViewModel what we picked
        });

        // 3. Listen to ViewModel selection changes (to sync UI)
        viewModel.selectedEdgeProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // -- EDIT MODE --
                // Sync List Selection if triggered from Graph
                edgeListView.getSelectionModel().select(newVal);

                // Populate fields
                txtWeight.setText(String.valueOf(newVal.weight()));
                txtCapacity.setText(String.valueOf(newVal.capacity()));
                txtFlow.setText(String.valueOf(newVal.flow()));
                chkDirected.setSelected(newVal.isDirected());

                // Update UI state
                lblHeader.setText("Editing Edge");
                lblSubHeader.setText(newVal.sourceId() + " -> " + newVal.targetId());
                btnUpdate.setDisable(false);
                
                // NEW: Enable Delete button
                btnDeleteEdge.setDisable(false);

                // Highlight in Graph
                redraw(newVal);
            } else {
                // -- DEFAULT MODE --
                edgeListView.getSelectionModel().clearSelection();
                lblHeader.setText("New Edge Defaults");
                lblSubHeader.setText("(Values for next draw)");
                btnUpdate.setDisable(true);
                btnDeleteEdge.setDisable(true);
                redraw(null);
            }
        });

        // Redraw when list changes (add/remove)
        viewModel.getNodesList().addListener((javafx.beans.Observable o) -> redraw(viewModel.selectedEdgeProperty().get()));
        viewModel.getEdgesList().addListener((javafx.beans.Observable o) -> redraw(viewModel.selectedEdgeProperty().get()));
    }

    @FXML
    public void onCanvasClick(MouseEvent event) {
        if (event.getTarget() == drawingPane) {
            viewModel.addNodeCommand(event.getX(), event.getY());
        }
    }

    // Logic to update the selected edge
    @FXML
    public void onUpdateEdge() {
        try {
            double w = Double.parseDouble(txtWeight.getText());
            double c = Double.parseDouble(txtCapacity.getText());
            double f = Double.parseDouble(txtFlow.getText());
            boolean d = chkDirected.isSelected();
            viewModel.updateSelectedEdgeCommand(w, c, f, d);
            // Refresh list view text
            edgeListView.refresh();
        } catch (NumberFormatException e) {
            lblStatus.setText("Error: Invalid numbers.");
        }
    }

    @FXML
    public void onDeselect() {
        viewModel.selectEdgeCommand(null);
    }
    
    @FXML
    public void onDeleteEdge() {
        viewModel.deleteSelectedEdgeCommand();
    }

    // --- Redraw Logic (Optimized for highlighting) ---

    private void redraw(Edge highlightedEdge) {
        drawingPane.getChildren().clear();

        // 1. Draw Edges
        for (Edge edge : viewModel.getEdgesList()) {
            Node src = findNode(edge.sourceId());
            Node tgt = findNode(edge.targetId());

            if (src != null && tgt != null) {
                boolean isSelected = (edge == highlightedEdge);
                drawEdge(src, tgt, edge, isSelected);
            }
        }

        // 2. Draw Nodes
        for (Node node : viewModel.getNodesList()) {
            drawNode(node);
        }
    }

    private void drawEdge(Node src, Node tgt, Edge edge, boolean isSelected) {
        Line line = new Line(src.x(), src.y(), tgt.x(), tgt.y());
        line.setStrokeWidth(isSelected ? 4 : 2); // Thicker if selected
        line.setStroke(isSelected ? Color.RED : Color.GRAY);

        // Click on line to select
        line.setOnMouseClicked(e -> {
            e.consume();
            viewModel.selectEdgeCommand(edge);
        });

        drawingPane.getChildren().add(line);

        if (edge.isDirected()) {
            drawArrowHead(src.x(), src.y(), tgt.x(), tgt.y(), isSelected);
        }

        // Label
        double midX = (src.x() + tgt.x()) / 2;
        double midY = (src.y() + tgt.y()) / 2;
        String info = (edge.capacity() > 0)
                ? String.format("W:%.0f [%.0f/%.0f]", edge.weight(), edge.flow(), edge.capacity())
                : String.format("W:%.0f", edge.weight());

        Text text = new Text(midX, midY - 5, info);
        text.setFill(isSelected ? Color.RED : Color.BLACK);
        drawingPane.getChildren().add(text);
    }

    // Separate Arrow logic to handle color/size
    private void drawArrowHead(double x1, double y1, double x2, double y2, boolean isSelected) {
        double r = 20;
        double angle = Math.atan2((y2 - y1), (x2 - x1));
        double fx = x2 - Math.cos(angle) * r;
        double fy = y2 - Math.sin(angle) * r;
        double arrowSize = 8;

        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(
                fx, fy,
                fx - arrowSize * Math.cos(angle - Math.PI / 6),
                fy - arrowSize * Math.sin(angle - Math.PI / 6),
                fx - arrowSize * Math.cos(angle + Math.PI / 6),
                fy - arrowSize * Math.sin(angle + Math.PI / 6)
        );
        arrow.setFill(isSelected ? Color.RED : Color.BLACK);
        drawingPane.getChildren().add(arrow);
    }

    private void drawNode(Node node) {
        Circle c = new Circle(node.x(), node.y(), 15);
        c.setFill(Color.CORNFLOWERBLUE);
        c.setStroke(Color.BLACK);

        c.setOnMouseClicked(e -> {
            e.consume();
            // Pass current input values as Defaults for new edge
            try {
                double w = Double.parseDouble(txtWeight.getText());
                double cVal = Double.parseDouble(txtCapacity.getText());
                double f = Double.parseDouble(txtFlow.getText());
                boolean d = chkDirected.isSelected();
                viewModel.onNodeClickCommand(node, w, cVal, f, d);
            } catch (Exception ex) {
                // Fallback if bad numbers
                viewModel.onNodeClickCommand(node, 1, 0, 0, false);
            }
        });

        // RIGHT CLICK: Context Menu for Deletion
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Delete Node");
        deleteItem.setOnAction(event -> viewModel.deleteNodeCommand(node));
        contextMenu.getItems().add(deleteItem);

        // Bind Context Menu to Circle
        c.setOnContextMenuRequested(e -> {
            contextMenu.show(c, e.getScreenX(), e.getScreenY());
        });

        Text t = new Text(node.x() - 5, node.y() + 5, node.id());
        t.setMouseTransparent(true);
        drawingPane.getChildren().addAll(c, t);
    }

    private Node findNode(String id) {
        return viewModel.getNodesList().stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    // Menu Actions same as before...
    @FXML void onSave() {
        FileChooser fc = new FileChooser();
        File f = fc.showSaveDialog(drawingPane.getScene().getWindow());
        viewModel.saveGraphCommand(f);
    }
    @FXML void onLoad() {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(drawingPane.getScene().getWindow());
        viewModel.loadGraphCommand(f);
    }
    @FXML void onClear() { viewModel.clearCommand(); }
    @FXML void onStats() { viewModel.calculateStatsCommand(); }
    @FXML void onExit() { System.exit(0); }
}