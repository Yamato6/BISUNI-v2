package mx.bisuni.desktop.service;

/** Error de negocio con un mensaje en español listo para mostrarse en la UI. */
public class ServiceException extends Exception {
    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
