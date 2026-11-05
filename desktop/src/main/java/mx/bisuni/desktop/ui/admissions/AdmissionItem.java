package mx.bisuni.desktop.ui.admissions;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import mx.bisuni.desktop.model.AspirantRow;
import mx.bisuni.desktop.model.AspirantStatus;

/** Fila editable de la tabla: guarda el estatus leído de la base y el elegido en pantalla. */
public final class AdmissionItem {

    private final AspirantRow row;
    private final ObjectProperty<AspirantStatus> status;
    private final BooleanBinding modified;

    public AdmissionItem(AspirantRow row) {
        this.row = row;
        this.status = new SimpleObjectProperty<>(row.status());
        this.modified = Bindings.createBooleanBinding(() -> status.get() != row.status(), status);
    }

    public AspirantRow row() {
        return row;
    }

    public AspirantStatus originalStatus() {
        return row.status();
    }

    public ObjectProperty<AspirantStatus> statusProperty() {
        return status;
    }

    public BooleanBinding modifiedBinding() {
        return modified;
    }

    public boolean isModified() {
        return modified.get();
    }

    /** Las filas ya aceptadas no se pueden editar. */
    public boolean isEditable() {
        return row.status() != AspirantStatus.Accepted;
    }

    public void revert() {
        status.set(row.status());
    }
}
