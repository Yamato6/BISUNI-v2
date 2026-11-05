package mx.bisuni.desktop.ui;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.function.Function;

/** Utilidades para las vistas de lista (tablas con filtros y búsqueda). */
public final class ListSupport {

    private ListSupport() {
    }

    /** Columna de solo lectura con el texto que devuelve {@code getter}. */
    public static <S> void text(TableColumn<S, String> col, Function<S, String> getter) {
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(getter.apply(c.getValue())));
    }

    /** Columna de solo lectura con un valor arbitrario; se muestra con {@code toString()}. */
    public static <S, T> void value(TableColumn<S, T> col, Function<S, T> getter) {
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(getter.apply(c.getValue())));
    }

    /** Columna con un valor que se formatea con {@code format} (p. ej. fechas). */
    public static <S, T> void formatted(TableColumn<S, T> col, Function<S, T> getter, Function<T, String> format) {
        value(col, getter);
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        });
    }

    /** Ejecuta {@code onOpen} al hacer doble clic en una fila con datos. */
    public static <S> void onDoubleClick(TableView<S> table, Consumer<S> onOpen) {
        table.setRowFactory(tv -> {
            TableRow<S> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    onOpen.accept(row.getItem());
                }
            });
            return row;
        });
    }

    /** Llama a {@code search} 300 ms después de que el usuario deja de escribir, o al pulsar Enter. */
    public static void debounce(TextField field, Runnable search) {
        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.setOnFinished(e -> search.run());
        field.textProperty().addListener((obs, old, text) -> pause.playFromStart());
        field.setOnAction(e -> {
            pause.stop();
            search.run();
        });
    }

    public static String count(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
