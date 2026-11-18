package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.dao.PeriodDao;
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.GroupData;
import mx.bisuni.desktop.model.GroupRow;
import mx.bisuni.desktop.model.GroupSubjectRow;
import mx.bisuni.desktop.model.Period;
import mx.bisuni.desktop.model.StudentStatus;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.SubjectRow;
import mx.bisuni.desktop.session.UserSession;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Grupos: datos generales, materias con su profesor y alumnos.
 * <p>
 * Reglas de la base que aquí se explican al usuario:
 * <ul>
 *   <li>El profesor de una materia debe estar habilitado para ella ({@code teacher_subject}).</li>
 *   <li>Cambiar el profesor actualiza el horario en cascada; si el nuevo profesor ya tiene clase
 *       a esa hora, el cambio se rechaza.</li>
 *   <li>Quitar una materia borra sus clases del horario (cascada), pero no se permite si ya tiene
 *       calificaciones.</li>
 *   <li>Los alumnos solo pueden estar en grupos de su carrera.</li>
 * </ul>
 */
public final class GroupService {

    private final Database db;
    private final GroupDao groups;
    private final SubjectDao subjects;
    private final StudentDao students;
    private final PeriodDao periods;

    public GroupService(Database db, GroupDao groups, SubjectDao subjects, StudentDao students, PeriodDao periods) {
        this.db = db;
        this.groups = groups;
        this.subjects = subjects;
        this.students = students;
        this.periods = periods;
    }

    // ---------------- Grupo ----------------

    public List<GroupRow> list(GroupDao.Filter filter) throws ServiceException {
        try {
            return db.withConnection(c -> groups.list(c, filter == null ? GroupDao.Filter.none() : filter));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de grupos.", e);
        }
    }

    public Optional<GroupRow> find(int idGroup) throws ServiceException {
        try {
            return db.withConnection(c -> groups.findById(c, idGroup));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el grupo.", e);
        }
    }

    public int create(GroupData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        GroupData d = validate(data);
        try {
            return db.actor(admin.idUser()).inTransaction(c -> {
                Period period = d.idPeriod() == null
                        ? periods.active(c).orElseThrow(() -> new Rejected(
                                "No hay un periodo activo. Crea o activa uno en Periodos."))
                        : periods.findById(c, d.idPeriod()).orElseThrow(() -> new Rejected("El periodo ya no existe."));
                if (period.isClosed()) {
                    throw new Rejected("El periodo " + period.name() + " ya está cerrado; no se le agregan grupos.");
                }
                return groups.insert(c, d, period.idPeriod());
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo crear el grupo.");
        }
    }

    /** La carrera solo se puede cambiar mientras el grupo no tenga alumnos ni materias. */
    public void update(int idGroup, GroupData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        GroupData d = validate(data);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                GroupRow current = editable(c, idGroup);
                if (current.idCareer() != d.idCareer()
                        && (current.studentCount() > 0 || current.subjectCount() > 0)) {
                    throw new Rejected("El grupo ya tiene alumnos o materias de su carrera; no se puede cambiar "
                            + "de carrera. Crea un grupo nuevo para la otra carrera.");
                }
                groups.update(c, idGroup, d);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo guardar el grupo.");
        }
    }

    // ---------------- Materias y profesores ----------------

    public List<GroupSubjectRow> subjects(int idGroup) throws ServiceException {
        try {
            return db.withConnection(c -> groups.subjects(c, idGroup));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar las materias del grupo.", e);
        }
    }

    /** Materias activas de la carrera del grupo que todavía no cursa. */
    public List<SubjectRow> availableSubjects(int idGroup) throws ServiceException {
        try {
            return db.withConnection(c -> {
                GroupRow g = groups.findById(c, idGroup).orElseThrow(() -> new SQLException("Grupo inexistente"));
                var assigned = groups.subjects(c, idGroup).stream().map(GroupSubjectRow::idSubject).toList();
                return subjects.list(c, new SubjectDao.Filter(g.idCareer(), null,
                                mx.bisuni.desktop.model.RecordStatus.Active, null)).stream()
                        .filter(s -> !assigned.contains(s.idSubject()))
                        .toList();
            });
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar las materias disponibles.", e);
        }
    }

    public List<Catalog.Teacher> eligibleTeachers(int idSubject) throws ServiceException {
        try {
            return db.withConnection(c -> groups.eligibleTeachers(c, idSubject));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar los profesores habilitados.", e);
        }
    }

    /** Agrega una materia de la misma carrera del grupo (sin profesor todavía). */
    public void addSubject(int idGroup, int idSubject, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                GroupRow g = editable(c, idGroup);
                SubjectRow s = subjects.findById(c, idSubject).orElseThrow(() -> new Rejected(
                        "La materia ya no existe."));
                if (s.idCareer() != g.idCareer()) {
                    throw new Rejected("«" + s.name() + "» es de " + s.careerName() + " y el grupo es de "
                            + g.careerName() + ".");
                }
                groups.addSubject(c, idGroup, idSubject);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo agregar la materia.");
        }
    }

    /** Quita la materia y sus clases del horario. Se rechaza si ya hay calificaciones. */
    public void removeSubject(int idGroup, int idSubject, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                editable(c, idGroup);
                return groups.removeSubject(c, idGroup, idSubject);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo quitar la materia.");
        }
    }

    /**
     * Asigna (o quita, con {@code null}) el profesor de una materia del grupo. El horario se
     * actualiza en cascada.
     */
    public void assignTeacher(int idGroup, int idSubject, Integer idTeacher, UserSession admin)
            throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                editable(c, idGroup);
                if (idTeacher == null) {
                    int scheduled = groups.scheduledHours(c, idGroup, idSubject);
                    if (scheduled > 0) {
                        throw new Rejected("La materia tiene " + scheduled + (scheduled == 1 ? " clase" : " clases")
                                + " en el horario. Para dejarla sin profesor, primero quítala del horario o asigna otro profesor.");
                    }
                }
                if (!groups.setTeacher(c, idGroup, idSubject, idTeacher)) {
                    throw new Rejected("La materia ya no está en el grupo. Recarga.");
                }
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo asignar el profesor.", Map.of(
                    "group_subject_id_teacher_id_subject_fkey",
                    "Ese profesor no está habilitado para impartir la materia. Asígnasela primero en Profesores.",
                    "schedule_teacher_clash",
                    "El profesor ya tiene clase en alguno de los bloques donde esta materia está en el horario del grupo."));
        }
    }

    // ---------------- Alumnos ----------------

    public List<StudentRow> students(int idGroup) throws ServiceException {
        try {
            return db.withConnection(c -> students.list(c,
                    new StudentDao.Filter(null, idGroup, false, null, null)));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar los alumnos del grupo.", e);
        }
    }

    /** Alumnos de la carrera del grupo que no están en él (sin grupo o en otro grupo). */
    public List<StudentRow> candidateStudents(int idGroup) throws ServiceException {
        try {
            return db.withConnection(c -> {
                GroupRow g = groups.findById(c, idGroup).orElseThrow(() -> new SQLException("Grupo inexistente"));
                return students.list(c, new StudentDao.Filter(g.idCareer(), null, false,
                                mx.bisuni.desktop.model.RecordStatus.Active, null)).stream()
                        .filter(s -> s.idGroup() == null || s.idGroup() != idGroup)
                        .toList();
            });
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar los alumnos disponibles.", e);
        }
    }

    public void addStudent(int idGroup, int idStudent, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                editable(c, idGroup);
                var s = students.findById(c, idStudent).orElseThrow(() -> new Rejected("El alumno ya no existe."));
                if (s.academicStatus() != StudentStatus.Active) {
                    throw new Rejected(s.firstName() + " " + s.lastName() + " está en " + s.academicStatus().label().toLowerCase()
                            + "; reactívalo en Alumnos antes de asignarle grupo.");
                }
                return groups.setStudentGroup(c, idStudent, idGroup);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo agregar el alumno al grupo.", Map.of(
                    "student_profile_id_group_id_career_fkey", "El alumno es de otra carrera que el grupo."));
        }
    }

    /** Deja al alumno sin grupo. Sus calificaciones no se tocan. */
    public void removeStudent(int idGroup, int idStudent, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).withConnection(c -> {
                var s = students.findById(c, idStudent);
                if (s.isEmpty() || s.get().idGroup() == null || s.get().idGroup() != idGroup) {
                    return false; // ya no estaba en el grupo: nada que hacer
                }
                return groups.setStudentGroup(c, idStudent, null);
            });
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo quitar el alumno del grupo.");
        }
    }

    // ---------------- Reinscripción y egreso ----------------

    /** Resultado de reinscribir un grupo al semestre siguiente. */
    public record Promotion(int idGroup, String groupName, String periodName, int students, int subjects) {
    }

    /** Resultado de egresar un grupo: cuántos egresaron y quiénes no (deben materias). */
    public record Graduation(int graduated, List<String> pending) {
    }

    /** ¿El grupo terminó el último semestre de su carrera? (entonces se egresa, no se reinscribe). */
    public boolean isLastSemester(int idGroup) throws ServiceException {
        try {
            return db.withConnection(c -> {
                GroupRow g = groups.findById(c, idGroup).orElseThrow(() -> new SQLException("Grupo inexistente"));
                return g.semester() >= groups.careerTotalSemesters(c, g.idCareer());
            });
        } catch (SQLException e) {
            throw new ServiceException("No se pudo consultar el grupo.", e);
        }
    }

    /**
     * Reinscripción: a partir de un grupo de un periodo cerrado crea el del semestre siguiente en
     * {@code idTargetPeriod} (mismo tutor, con las materias activas de ese semestre y sin profesor) y
     * le pasa a los alumnos activos, sumándoles un semestre. Sus calificaciones quedan en el grupo
     * anterior (kárdex). Los alumnos con materias reprobadas se reinscriben igual: el recursamiento
     * todavía no está implementado.
     */
    public Promotion promote(int idSourceGroup, int idTargetPeriod, String newName, UserSession admin)
            throws ServiceException {
        Guards.requireAdmin(admin);
        String name = Validation.required(newName, "el nombre del nuevo grupo", 50).toUpperCase(java.util.Locale.ROOT);
        try {
            return db.actor(admin.idUser()).inTransaction(c -> {
                GroupRow src = groups.findById(c, idSourceGroup)
                        .orElseThrow(() -> new Rejected("El grupo ya no existe. Recarga la lista."));
                if (!src.isHistory()) {
                    throw new Rejected("Solo se reinscriben grupos de un periodo cerrado; " + src.periodName()
                            + " sigue " + src.periodStatus().label().toLowerCase() + ".");
                }
                Period target = periods.findById(c, idTargetPeriod)
                        .orElseThrow(() -> new Rejected("El periodo destino ya no existe."));
                if (target.isClosed()) {
                    throw new Rejected("El periodo " + target.name() + " ya está cerrado.");
                }
                int total = groups.careerTotalSemesters(c, src.idCareer());
                if (src.semester() >= total) {
                    throw new Rejected(src.name() + " terminó el último semestre de la carrera (" + total
                            + "°): sus alumnos egresan, no se reinscriben.");
                }
                int semester = src.semester() + 1;
                int idGroup = groups.insert(c, new GroupData(name, src.idCareer(), semester, src.idTutor(),
                        mx.bisuni.desktop.model.RecordStatus.Active), target.idPeriod());
                int subjectCount = groups.addSubjectsOfSemester(c, idGroup, src.idCareer(), semester);
                int moved = groups.moveActiveStudents(c, idSourceGroup, idGroup, semester);
                if (moved == 0) {
                    throw new Rejected(src.name() + " no tiene alumnos activos que reinscribir.");
                }
                return new Promotion(idGroup, name, target.name(), moved, subjectCount);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo reinscribir el grupo.");
        }
    }

    /**
     * Egreso: los alumnos activos de un grupo que terminó el último semestre y no deben materias
     * pasan a egresados (sin grupo). Los que deben materias se quedan y se listan.
     */
    public Graduation graduate(int idSourceGroup, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            return db.actor(admin.idUser()).inTransaction(c -> {
                GroupRow src = groups.findById(c, idSourceGroup)
                        .orElseThrow(() -> new Rejected("El grupo ya no existe. Recarga la lista."));
                if (!src.isHistory()) {
                    throw new Rejected("Primero se cierra el periodo " + src.periodName() + ".");
                }
                int total = groups.careerTotalSemesters(c, src.idCareer());
                if (src.semester() < total) {
                    throw new Rejected(src.name() + " está en " + src.semester() + "° semestre de " + total
                            + ": se reinscribe al siguiente, no egresa.");
                }
                int graduated = groups.graduateStudents(c, idSourceGroup);
                return new Graduation(graduated, groups.activeStudentNames(c, idSourceGroup));
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo registrar el egreso.");
        }
    }

    /** El grupo, si existe y su periodo no está cerrado (los grupos de periodos cerrados son historial). */
    private GroupRow editable(Connection c, int idGroup) throws SQLException {
        GroupRow g = groups.findById(c, idGroup).orElseThrow(() -> new Rejected("El grupo ya no existe. Recarga la lista."));
        if (g.isHistory()) {
            throw new Rejected("El grupo " + g.name() + " es del periodo " + g.periodName()
                    + ", que ya cerró: es solo historial y no se modifica.");
        }
        return g;
    }

    static GroupData validate(GroupData d) throws ServiceException {
        if (d == null) {
            throw new ServiceException("Faltan los datos del grupo.");
        }
        String name = Validation.required(d.name(), "el nombre del grupo", 50).toUpperCase(java.util.Locale.ROOT);
        if (d.idCareer() <= 0) {
            throw new ServiceException("Selecciona la carrera.");
        }
        Validation.semester(d.semester());
        if (d.status() == null) {
            throw new ServiceException("Selecciona el estatus.");
        }
        return new GroupData(name, d.idCareer(), d.semester(), d.idTutor(), d.status(), d.idPeriod());
    }
}
