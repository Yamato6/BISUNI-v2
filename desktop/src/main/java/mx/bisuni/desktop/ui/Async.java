package mx.bisuni.desktop.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Ejecuta trabajo de base de datos fuera del hilo de JavaFX y regresa el resultado a él. */
public final class Async {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bisuni-db");
        t.setDaemon(true);
        return t;
    });

    private Async() {
    }

    /**
     * @param work      se ejecuta en segundo plano
     * @param onSuccess se ejecuta en el hilo de JavaFX con el resultado
     * @param onError   se ejecuta en el hilo de JavaFX con la excepción
     */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        EXECUTOR.execute(task);
    }
}
