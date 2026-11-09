package mx.bisuni.desktop.ui.teacher;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.ScheduleEntry;
import mx.bisuni.desktop.model.TimeSlot;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ScheduleGrid;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.List;

/** Horario semanal del profesor en sesión (solo consulta; sale de v_teacher_schedule). */
public class MyScheduleController implements ShellController.ContentView {

    private record Loaded(List<TimeSlot> slots, List<ScheduleEntry> entries) {
    }

    @FXML private VBox root;
    @FXML private Label totalLabel;
    @FXML private ScheduleGrid grid;

    public void init(AppContext ctx, UserSession session) {
        grid.setSecondLine(ScheduleEntry::groupName);
        grid.setEditable(false);
        Async.run(() -> new Loaded(ctx.schedule().timeSlots(), ctx.schedule().forTeacher(session.profileId())),
                d -> {
                    grid.setSlots(d.slots());
                    grid.setEntries(d.entries());
                    int n = d.entries().size();
                    totalLabel.setText(n + (n == 1 ? " clase" : " clases") + " por semana");
                },
                error -> Dialogs.error(root, error));
    }

    @Override
    public Node root() {
        return root;
    }

    /** Para la prueba de humo. */
    public int shownEntries() {
        return grid.entryCount();
    }
}
