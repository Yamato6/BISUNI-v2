package mx.bisuni.desktop.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import mx.bisuni.desktop.App;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.login.LoginController;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;

/** Navegación en una sola ventana: cambia la raíz de la escena entre Login y el shell. */
public final class Navigator {

    /** Vista cargada desde FXML junto con su controlador. */
    public record View<C>(Parent root, C controller) {
    }

    private final Stage stage;
    private final AppContext context;
    private final Scene scene;

    public Navigator(Stage stage, AppContext context) {
        this.stage = stage;
        this.context = context;
        this.scene = new Scene(new javafx.scene.layout.Pane(), 1180, 760);
        scene.getStylesheets().add(resource("css/bisuni.css").toExternalForm());
        stage.setScene(scene);
    }

    public AppContext context() {
        return context;
    }

    public void showLogin() {
        View<LoginController> view = load("ui/login/login.fxml");
        view.controller().init(this);
        scene.setRoot(view.root());
        stage.setTitle("BISUNI - Acceso de personal");
    }

    public void showShell(UserSession session) {
        View<ShellController> view = load("ui/shell/shell.fxml");
        view.controller().init(this, session);
        scene.setRoot(view.root());
        stage.setTitle("BISUNI - " + session.role().label());
    }

    /** Carga un FXML relativo a {@code mx/bisuni/desktop/}. */
    public static <C> View<C> load(String path) {
        FXMLLoader loader = new FXMLLoader(resource(path));
        try {
            Parent root = loader.load();
            return new View<>(root, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar " + path, e);
        }
    }

    public static URL resource(String path) {
        return Objects.requireNonNull(App.class.getResource(path), "Recurso no encontrado: " + path);
    }
}
