package mx.bisuni.desktop;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import mx.bisuni.desktop.ui.Navigator;

import java.util.Objects;

/** Punto de entrada único de la app de escritorio (en la v1 cada JFrame tenía su propio main). */
public class App extends Application {

    private AppContext context;

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        context = AppContext.create();

        stage.setTitle("BISUNI");
        stage.getIcons().add(new Image(Objects.requireNonNull(
                App.class.getResourceAsStream("img/logo.png"))));
        stage.setMinWidth(1024);
        stage.setMinHeight(700);

        new Navigator(stage, context).showLogin();
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
