package mx.bisuni.desktop.config;

/** Error de configuración con un mensaje listo para mostrarse al usuario. */
public class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }
}
