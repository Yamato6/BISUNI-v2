package mx.bisuni.desktop.ui.dashboard;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Dashboard;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.List;

/** Panel de inicio del admin: cifras clave y alertas del periodo activo. */
public class DashboardController implements ShellController.ContentView {

    private static final int MAX_ITEMS = 6;

    @FXML private ScrollPane root;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private HBox cards;
    @FXML private BarChart<Number, String> careerChart;
    @FXML private VBox failingBox;
    @FXML private VBox alertsBox;

    private boolean loaded;

    public void init(AppContext ctx, UserSession session) {
        titleLabel.setText("Hola, " + session.firstName());
        Async.run(() -> ctx.dashboard().load(session), this::show, error -> {
            subtitleLabel.setText("No se pudo cargar el panel.");
            Dialogs.error(root, error);
        });
    }

    @Override
    public Node root() {
        return root;
    }

    /** Para la prueba de humo. */
    public boolean isLoaded() {
        return loaded;
    }

    private void show(Dashboard d) {
        subtitleLabel.setText(d.activePeriod() == null
                ? "No hay periodo activo. Actívalo en Periodos para que los profesores capturen calificaciones y asistencia."
                : "Periodo activo: " + d.activePeriod() + ".");
        cards.getChildren().setAll(
                card(d.activePeriod() == null ? "—" : d.activePeriod(), "Periodo activo", d.groups() + " grupos"),
                card(String.valueOf(d.pendingAspirants()), "Aspirantes pendientes", "Revísalos en Admisiones"),
                card(String.valueOf(d.activeStudents()), "Alumnos activos", null),
                card(String.valueOf(d.openActas()), "Actas abiertas", "Del periodo activo"));

        XYChart.Series<Number, String> series = new XYChart.Series<>();
        for (Dashboard.CareerCount c : d.studentsByCareer()) {
            series.getData().add(new XYChart.Data<>(c.students(), c.career()));
        }
        careerChart.getData().setAll(List.of(series));
        // Escala entera y "redonda": con el rango automático las marcas salen como 2.5, 7.5... y se enciman.
        int max = d.studentsByCareer().stream().mapToInt(Dashboard.CareerCount::students).max().orElse(0);
        int step = max <= 10 ? 2 : max <= 50 ? 5 : max <= 100 ? 10 : 50;
        NumberAxis axis = (NumberAxis) careerChart.getXAxis();
        axis.setAutoRanging(false);
        axis.setLowerBound(0);
        axis.setUpperBound(Math.max(step, Math.ceil((double) max / step) * step));
        axis.setTickUnit(step);

        failingBox.getChildren().clear();
        if (d.failing().isEmpty()) {
            failingBox.getChildren().add(item("Ninguna materia tiene reprobados con calificación final.", "ok-item"));
        }
        for (Dashboard.FailingSubject f : d.failing()) {
            Label name = new Label(f.subject() + " · " + f.group() + " (" + f.period() + ")");
            name.setWrapText(true);
            ProgressBar bar = new ProgressBar(f.graded() == 0 ? 0 : (double) f.failed() / f.graded());
            bar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bar, Priority.ALWAYS);
            Label pct = new Label(f.failed() + " de " + f.graded() + " reprobados (" + f.pct() + " %)");
            pct.setMinWidth(Region.USE_PREF_SIZE);
            pct.getStyleClass().add("stat-label");
            HBox line = new HBox(10, bar, pct);
            line.setAlignment(Pos.CENTER_LEFT);
            failingBox.getChildren().add(new VBox(3, name, line));
        }

        alertsBox.getChildren().clear();
        section("Materias sin profesor", d.missingTeacher(), "Asígnalos en Grupos → Materias y alumnos.");
        section("Horario incompleto", d.incompleteSchedule(), "Complétalo en Horarios.");
        section("Alumnos en riesgo por faltas (más del 20 %)", d.atRisk().stream()
                .map(r -> r.name() + " · " + r.group() + " · " + r.subject() + ": " + r.absencePct() + " %")
                .toList(), null);
        if (alertsBox.getChildren().isEmpty()) {
            alertsBox.getChildren().add(item("Todo en orden: las materias tienen profesor y horario completo, "
                    + "y ningún alumno supera el 20 % de faltas.", "ok-item"));
        }
        loaded = true;
    }

    private void section(String title, List<String> items, String hint) {
        if (items.isEmpty()) {
            return;
        }
        Label header = new Label(title + " (" + items.size() + ")" + (hint == null ? "" : " — " + hint));
        header.setStyle("-fx-font-weight: bold;");
        alertsBox.getChildren().add(header);
        items.stream().limit(MAX_ITEMS).forEach(i -> alertsBox.getChildren().add(item(i, "alert-item")));
        if (items.size() > MAX_ITEMS) {
            alertsBox.getChildren().add(new Label("… y " + (items.size() - MAX_ITEMS) + " más"));
        }
    }

    private static Label item(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static VBox card(String value, String label, String hint) {
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        VBox box = new VBox(2, l, v);
        if (hint != null) {
            Label h = new Label(hint);
            h.getStyleClass().add("stat-label");
            box.getChildren().add(h);
        }
        box.getStyleClass().add("stat-card");
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }
}
