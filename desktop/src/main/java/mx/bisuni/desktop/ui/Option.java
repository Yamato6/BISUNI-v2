package mx.bisuni.desktop.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Opción de un combo de filtro: etiqueta visible y valor ({@code null} = "Todos"). */
public record Option<T>(String label, T value) {

    @Override
    public String toString() {
        return label;
    }

    /** Lista con una opción "todos" al inicio seguida de cada valor con su {@code toString()}. */
    public static <T> List<Option<T>> withAll(String allLabel, Collection<T> values) {
        List<Option<T>> out = new ArrayList<>();
        out.add(new Option<>(allLabel, null));
        for (T v : values) {
            out.add(new Option<>(v.toString(), v));
        }
        return out;
    }
}
