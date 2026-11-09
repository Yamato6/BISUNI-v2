package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.GradeDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.GradeRow;
import mx.bisuni.desktop.session.UserSession;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Captura de calificaciones. Solo el profesor asignado a la clase ({@code group_subject.id_teacher})
 * puede verla y capturarla. Se guarda con UPSERT; {@code final_grade} la calcula la base.
 */
public final class GradeService {

    private final Database db;
    private final GradeDao grades;
    private final TeachingDao teaching;

    public GradeService(Database db, GradeDao grades, TeachingDao teaching) {
        this.db = db;
        this.grades = grades;
        this.teaching = teaching;
    }

    public List<GradeRow> sheet(int idGroup, int idSubject, UserSession teacher) throws ServiceException {
        Guards.requireTeacher(teacher);
        try {
            return db.withConnection(c -> {
                requireTeaches(c, teacher, idGroup, idSubject);
                return grades.sheet(c, idGroup, idSubject);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar las calificaciones.", e);
        }
    }

    /**
     * Guarda los renglones modificados en una sola transacción. Solo se aceptan alumnos que aparecen
     * en la hoja de la clase (están en el grupo o ya tenían calificaciones en ella).
     *
     * @return renglones guardados
     */
    public int save(int idGroup, int idSubject, List<GradeInput> changes, UserSession teacher)
            throws ServiceException {
        Guards.requireTeacher(teacher);
        if (changes == null || changes.isEmpty()) {
            return 0;
        }
        for (GradeInput g : changes) {
            check(g.parcial1(), "parcial 1");
            check(g.parcial2(), "parcial 2");
            check(g.parcial3(), "parcial 3");
        }
        try {
            return db.inTransaction(c -> {
                requireTeaches(c, teacher, idGroup, idSubject);
                Map<Integer, GradeRow> allowed = grades.sheet(c, idGroup, idSubject).stream()
                        .collect(Collectors.toMap(GradeRow::idStudent, Function.identity()));
                int saved = 0;
                for (GradeInput g : changes) {
                    GradeRow current = allowed.get(g.idStudent());
                    if (current == null) {
                        throw new Rejected("Uno de los alumnos ya no pertenece a esta clase. Recarga la lista.");
                    }
                    boolean empty = g.parcial1() == null && g.parcial2() == null && g.parcial3() == null;
                    boolean hadRow = current.parcial1() != null || current.parcial2() != null
                            || current.parcial3() != null;
                    if (empty && !hadRow) {
                        continue; // nada que guardar
                    }
                    grades.upsert(c, idGroup, idSubject, g);
                    saved++;
                }
                return saved;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudieron guardar las calificaciones; no se guardó ninguna.");
        }
    }

    /**
     * Convierte lo que se escribió en una celda: vacío → {@code null}; acepta coma decimal.
     *
     * @param field para el mensaje, p. ej. "el parcial 2 de Pérez, Juan"
     */
    public static BigDecimal parse(String text, String field) throws ServiceException {
        if (text == null || text.isBlank()) {
            return null;
        }
        BigDecimal v;
        try {
            v = new BigDecimal(text.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ServiceException("«" + text.strip() + "» no es una calificación válida en " + field + ".");
        }
        check(v, field);
        return v.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Lo mismo que calcula la base: promedio de los 3 parciales a 2 decimales, o {@code null}. */
    public static BigDecimal finalGrade(BigDecimal p1, BigDecimal p2, BigDecimal p3) {
        if (p1 == null || p2 == null || p3 == null) {
            return null;
        }
        return p1.add(p2).add(p3).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
    }

    private static void check(BigDecimal v, String field) throws ServiceException {
        if (v == null) {
            return;
        }
        if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.TEN) > 0) {
            throw new ServiceException("Las calificaciones van de 0 a 10 (" + field + ").");
        }
        if (v.stripTrailingZeros().scale() > 2) {
            throw new ServiceException("Máximo 2 decimales (" + field + ").");
        }
    }

    private void requireTeaches(java.sql.Connection c, UserSession teacher, int idGroup, int idSubject)
            throws SQLException {
        if (!teaching.teaches(c, teacher.profileId(), idGroup, idSubject)) {
            throw new Rejected("No impartes esa materia en ese grupo.");
        }
    }
}
