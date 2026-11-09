package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.GradeRow;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Calificaciones. {@code final_grade} es columna generada: nunca se escribe. */
public final class GradeDao {

    /**
     * Hoja de una clase: los alumnos que están hoy en el grupo y, además, los que ya no están pero
     * tienen calificaciones de esta clase (para no perderlas de vista).
     */
    public List<GradeRow> sheet(Connection conn, int idGroup, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT sp.id_student, u.last_name, u.first_name, u.username,
                       coalesce(sp.id_group = ?, false) AS in_group,
                       gr.parcial1, gr.parcial2, gr.parcial3, gr.final_grade
                FROM student_profile sp
                JOIN users u ON u.id_user = sp.id_user
                LEFT JOIN grades gr ON gr.id_student = sp.id_student AND gr.id_group = ? AND gr.id_subject = ?
                WHERE sp.id_group = ? OR gr.id_grade IS NOT NULL
                ORDER BY u.last_name, u.first_name, sp.id_student
                """)) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idGroup);
            ps.setInt(3, idSubject);
            ps.setInt(4, idGroup);
            List<GradeRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new GradeRow(rs.getInt("id_student"), rs.getString("last_name"),
                            rs.getString("first_name"), rs.getString("username"), rs.getBoolean("in_group"),
                            rs.getBigDecimal("parcial1"), rs.getBigDecimal("parcial2"),
                            rs.getBigDecimal("parcial3"), rs.getBigDecimal("final_grade")));
                }
            }
            return out;
        }
    }

    /** Captura con UPSERT sobre {@code UNIQUE (id_student, id_subject, id_group)}. */
    public void upsert(Connection conn, int idGroup, int idSubject, GradeInput g) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO grades (id_student, id_group, id_subject, parcial1, parcial2, parcial3)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id_student, id_subject, id_group) DO UPDATE
                SET parcial1 = EXCLUDED.parcial1, parcial2 = EXCLUDED.parcial2, parcial3 = EXCLUDED.parcial3
                """)) {
            ps.setInt(1, g.idStudent());
            ps.setInt(2, idGroup);
            ps.setInt(3, idSubject);
            setDecimal(ps, 4, g.parcial1());
            setDecimal(ps, 5, g.parcial2());
            setDecimal(ps, 6, g.parcial3());
            ps.executeUpdate();
        }
    }

    private static void setDecimal(PreparedStatement ps, int i, BigDecimal v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.NUMERIC);
        } else {
            ps.setBigDecimal(i, v);
        }
    }
}
