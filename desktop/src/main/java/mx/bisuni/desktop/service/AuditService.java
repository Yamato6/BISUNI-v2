package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AuditDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AuditRow;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bitácora (solo admins). Los cambios los registra la base con triggers, así que también quedan
 * los que se hacen desde la web o con SQL directo; aquí solo se consultan y se traducen.
 */
public final class AuditService {

    public static final int MAX_ROWS = 500;

    /** Entidades auditadas, en el orden del filtro. */
    public static final Map<String, String> ENTITIES = new LinkedHashMap<>();

    static {
        ENTITIES.put("grades", "Calificaciones");
        ENTITIES.put("group_subject", "Materias de grupo y actas");
        ENTITIES.put("student_profile", "Alumnos");
        ENTITIES.put("aspirant_profile", "Admisiones");
        ENTITIES.put("users", "Usuarios");
        ENTITIES.put("academic_period", "Periodos");
        ENTITIES.put("career", "Carreras");
    }

    private static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("email", "Correo"), Map.entry("first_name", "Nombre"), Map.entry("last_name", "Apellidos"),
            Map.entry("role", "Rol"), Map.entry("status", "Estatus"), Map.entry("password_hash", "Contraseña"),
            Map.entry("must_change_password", "Debe cambiar contraseña"), Map.entry("career_name", "Nombre"),
            Map.entry("code", "Clave"), Map.entry("total_semesters", "Semestres"), Map.entry("name", "Nombre"),
            Map.entry("start_date", "Inicio"), Map.entry("end_date", "Fin"), Map.entry("admissions_open", "Convocatoria abierta"),
            Map.entry("enrollment_number", "Matrícula"), Map.entry("id_career", "Carrera (id)"),
            Map.entry("semester", "Semestre"), Map.entry("academic_status", "Situación"), Map.entry("id_group", "Grupo (id)"),
            Map.entry("id_teacher", "Profesor (id)"), Map.entry("grades_closed_at", "Acta cerrada"),
            Map.entry("parcial1", "Parcial 1"), Map.entry("parcial2", "Parcial 2"), Map.entry("parcial3", "Parcial 3"));

    private static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("true", "Sí"), Map.entry("false", "No"),
            Map.entry("Active", "Activo"), Map.entry("Inactive", "Inactivo"), Map.entry("Leave", "Baja temporal"),
            Map.entry("Dropped", "Baja definitiva"), Map.entry("Graduated", "Egresado"),
            Map.entry("Pending", "Pendiente"), Map.entry("Accepted", "Aceptado"), Map.entry("Rejected", "Rechazado"),
            Map.entry("Planned", "Planeado"), Map.entry("Closed", "Cerrado"),
            Map.entry("Admin", "Administrador"), Map.entry("Teacher", "Profesor"), Map.entry("Student", "Alumno"),
            Map.entry("Aspirant", "Aspirante"));

    private final Database db;
    private final AuditDao audit;

    public AuditService(Database db, AuditDao audit) {
        this.db = db;
        this.audit = audit;
    }

    public List<AuditRow> list(AuditDao.Filter filter, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new ServiceException("La fecha final es anterior a la inicial.");
        }
        int limit = filter.limit() <= 0 ? MAX_ROWS : Math.min(filter.limit(), MAX_ROWS);
        var f = new AuditDao.Filter(filter.from(), filter.to(), filter.entity(), filter.search(), limit);
        try {
            return db.withConnection(c -> audit.list(c, f));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo consultar la bitácora.", e);
        }
    }

    public static String entityLabel(String entity) {
        return ENTITIES.getOrDefault(entity, entity);
    }

    public static String actionLabel(String action) {
        return switch (action) {
            case "insert" -> "Alta";
            case "update" -> "Cambio";
            case "delete" -> "Baja";
            default -> action;
        };
    }

    /** "Parcial 1: 8.00 → 9.50", una línea por campo; las altas sin cambios dicen "Registro creado". */
    public static String describe(AuditRow row) {
        if (row.details() == null || row.details().isBlank()) {
            return "insert".equals(row.action()) ? "Registro creado." : "delete".equals(row.action()) ? "Registro borrado." : "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : row.details().split("\n")) {
            String[] p = line.split("\\|", -1);
            String field = FIELDS.getOrDefault(p[0], p[0]);
            if (out.length() > 0) {
                out.append('\n');
            }
            if (p.length == 3 && p[1].isEmpty() && !p[2].isEmpty() && "password_hash".equals(p[0])) {
                out.append(field).append(": cambiada");
            } else if (p.length == 3) {
                out.append(field).append(": ").append(value(p[1])).append(" → ").append(value(p[2]));
            } else {
                out.append(line);
            }
        }
        return out.toString();
    }

    private static String value(String v) {
        if (v.isEmpty()) {
            return "—";
        }
        if (v.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
            return v.substring(0, 16).replace('T', ' '); // marca de tiempo → "2026-06-19 13:00"
        }
        return VALUES.getOrDefault(v, v);
    }
}
