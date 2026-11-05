package mx.bisuni.desktop;

/**
 * main() en una clase que no extiende Application. Permite ejecutar la app desde el classpath
 * (IDE o jar) sin que el launcher de Java exija los módulos de JavaFX.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
