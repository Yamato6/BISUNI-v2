package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.Catalog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Catálogos de solo lectura para combos y filtros. */
public final class CatalogDao {

    public List<Catalog.Career> activeCareers(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id_career, career_name FROM career WHERE status = 'Active' ORDER BY career_name");
             ResultSet rs = ps.executeQuery()) {
            List<Catalog.Career> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Catalog.Career(rs.getInt(1), rs.getString(2)));
            }
            return out;
        }
    }

    /** Grupos activos del periodo activo, ordenados por carrera, semestre y nombre. */
    public List<Catalog.Group> activeGroups(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT g.id_group, g.group_name, g.id_career, g.semester
                FROM school_groups g JOIN academic_period p ON p.id_period = g.id_period
                WHERE g.status = 'Active' AND p.status = 'Active'
                ORDER BY g.id_career, g.semester, g.group_name
                """);
             ResultSet rs = ps.executeQuery()) {
            List<Catalog.Group> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Catalog.Group(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4)));
            }
            return out;
        }
    }

    /** Profesores con cuenta activa, por apellido. */
    public List<Catalog.Teacher> activeTeachers(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT t.id_teacher, u.first_name || ' ' || u.last_name
                FROM teacher_profile t JOIN users u ON u.id_user = t.id_user
                WHERE u.status = 'Active'
                ORDER BY u.last_name, u.first_name
                """);
             ResultSet rs = ps.executeQuery()) {
            List<Catalog.Teacher> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Catalog.Teacher(rs.getInt(1), rs.getString(2)));
            }
            return out;
        }
    }

    public List<Catalog.Subject> activeSubjects(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT s.id_subject, s.subject_name, c.career_name, s.semester
                FROM subjects s JOIN career c ON c.id_career = s.id_career
                WHERE s.status = 'Active'
                ORDER BY c.career_name, s.semester, s.subject_name
                """);
             ResultSet rs = ps.executeQuery()) {
            List<Catalog.Subject> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Catalog.Subject(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
            }
            return out;
        }
    }
}
