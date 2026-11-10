package mx.bisuni.desktop.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Operaciones de alta de alumno al aceptar a un aspirante. Se usan dentro de una transacción. */
public final class AdmissionDao {

    public void promoteToStudent(Connection conn, int idUser) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET role = 'Student' WHERE id_user = ? AND role = 'Aspirant'")) {
            ps.setInt(1, idUser);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("El usuario " + idUser + " no tiene rol de aspirante");
            }
        }
    }

    /**
     * Crea el perfil del alumno (semestre 1, activo y sin grupo) con su matrícula: año de la
     * convocatoria del aspirante + clave de la carrera + consecutivo ({@code next_enrollment_number}).
     */
    public int createStudentProfile(Connection conn, int idAspirant, int idUser, int idCareer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO student_profile (id_user, id_career, enrollment_number)
                SELECT ?, ?, next_enrollment_number(?, extract(year FROM p.start_date)::int)
                FROM aspirant_profile a JOIN academic_period p ON p.id_period = a.id_period
                WHERE a.id_aspirant = ?
                RETURNING id_student""")) {
            ps.setInt(1, idUser);
            ps.setInt(2, idCareer);
            ps.setInt(3, idCareer);
            ps.setInt(4, idAspirant);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void createEnrollment(Connection conn, int idAspirant, int idStudent, int approvedByAdmin)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO enrollment (id_aspirant, id_student, approved_by) VALUES (?, ?, ?)")) {
            ps.setInt(1, idAspirant);
            ps.setInt(2, idStudent);
            ps.setInt(3, approvedByAdmin);
            ps.executeUpdate();
        }
    }
}
