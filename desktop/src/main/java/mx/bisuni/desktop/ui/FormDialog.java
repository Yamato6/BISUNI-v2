package mx.bisuni.desktop.ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.service.ServiceException;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo modal de formulario: filas etiqueta/campo, una línea de error y un botón de guardar
 * que ejecuta el guardado en segundo plano. Si el guardado falla, el diálogo sigue abierto con
 * el mensaje de error para que el usuario corrija sin perder lo capturado.
 */
public final class FormDialog {

    /**
     * Se ejecuta en el hilo de JavaFX al pulsar guardar: lee y valida los campos y devuelve el
     * trabajo de base de datos que se correrá en segundo plano.
     */
    @FunctionalInterface
    public interface SaveAction {
        Callable<?> prepare() throws ServiceException;
    }

    private static final Logger LOG = Logger.getLogger(FormDialog.class.getName());

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final ButtonType saveType;
    private final GridPane grid = new GridPane();
    private final Label errorLabel = new Label();
    private int row;
    private boolean saved;

    public FormDialog(Node owner, String title, String saveText) {
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        saveType = new ButtonType(saveText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType,
                new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));

        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(140);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setMinWidth(300);
        grid.getColumnConstraints().addAll(labels, fields);

        errorLabel.getStyleClass().add("form-error");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(460);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        VBox content = new VBox(14, grid, errorLabel);
        content.setPadding(new Insets(8, 4, 4, 4));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(Navigator.resource("css/bisuni.css").toExternalForm());
        // La X de la ventana tampoco cierra mientras se guarda.
        dialog.setOnCloseRequest(e -> {
            if (grid.isDisabled() && !saved) {
                e.consume();
            }
        });
    }

    /** Agrega una fila con etiqueta y campo. Devuelve el campo para encadenar. */
    public <T extends Node> T add(String label, T field) {
        Label l = new Label(label);
        l.setLabelFor(field);
        GridPane.setValignment(l, javafx.geometry.VPos.CENTER);
        grid.add(l, 0, row);
        grid.add(field, 1, row);
        if (field instanceof javafx.scene.control.Control c) {
            c.setMaxWidth(Double.MAX_VALUE);
        }
        row++;
        return field;
    }

    /** Agrega un título de sección que ocupa ambas columnas. */
    public void section(String title) {
        Label l = new Label(title);
        l.getStyleClass().add("form-section");
        grid.add(l, 0, row++, 2, 1);
    }

    /** Agrega un nodo que ocupa ambas columnas (notas, contenedores dinámicos). */
    public <T extends Node> T addFull(T node) {
        grid.add(node, 0, row++, 2, 1);
        return node;
    }

    public void showError(String message) {
        errorLabel.setText(message);
        boolean show = message != null && !message.isBlank();
        errorLabel.setManaged(show);
        errorLabel.setVisible(show);
        if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        }
    }

    /**
     * Muestra el diálogo y lo mantiene abierto hasta que se guarde con éxito o se cancele.
     *
     * @return {@code true} si se guardó.
     */
    public boolean showAndSave(SaveAction action) {
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.setDefaultButton(true);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); // el diálogo se cierra solo si el guardado tiene éxito
            Callable<?> work;
            try {
                work = action.prepare();
            } catch (ServiceException e) {
                showError(e.getMessage());
                return;
            }
            showError(null);
            setBusy(save, true);
            Async.run(work,
                    result -> {
                        saved = true;
                        dialog.setResult(saveType);
                        dialog.close();
                    },
                    error -> {
                        setBusy(save, false);
                        if (error instanceof ServiceException se) {
                            if (se.getCause() != null) {
                                LOG.log(Level.WARNING, se.getMessage(), se.getCause());
                            }
                            showError(se.getMessage());
                        } else {
                            LOG.log(Level.SEVERE, "Error inesperado al guardar", error);
                            showError("Ocurrió un error inesperado: " + error.getMessage());
                        }
                    });
        });
        dialog.showAndWait();
        return saved;
    }

    /** Mientras se guarda no se puede editar ni cerrar el diálogo. */
    private void setBusy(Button save, boolean busy) {
        for (ButtonType type : dialog.getDialogPane().getButtonTypes()) {
            dialog.getDialogPane().lookupButton(type).setDisable(busy);
        }
        grid.setDisable(busy);
    }

    /** Etiqueta de nota en gris para explicar algo dentro del formulario. */
    public static Label note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(460);
        l.getStyleClass().add("form-note");
        l.setAlignment(Pos.TOP_LEFT);
        return l;
    }
}
