package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.AspirantRow;
import mx.bisuni.desktop.model.AspirantStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AspirantDao {

    /** Datos que hacen falta para dar de alta al alumno al aceptar. */
    public record Transition(int idUser, int idCareer) {
    }

    private static final String LIST = """
            SELECT a.id_aspirant, u.username, u.first_name, u.last_name, u.email, a.curp,
                   c.career_name, a.application_date, a.status::text AS status
            FROM aspirant_profile a
            JOIN users  u ON u.id_user   = a.id_user
            JOIN career c ON c.id_career = a.id_career
            WHERE (?::aspirant_status IS NULL OR a.status = ?::aspirant_status)
            ORDER BY a.application_date DESC, a.id_aspirant DESC
            """;

    /** Lista aspirantes; {@code filter == null} significa todos. */
    public List<AspirantRow> list(Connection conn, AspirantStatus filter) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(LIST)) {
            String f = filter == null ? null : filter.name();
            ps.setString(1, f);
            ps.setString(2, f);
            List<AspirantRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AspirantRow(
                            rs.getInt("id_aspirant"),
                            rs.getString("username"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("email"),
                            rs.getString("curp"),
                            rs.getString("career_name"),
                            rs.getObject("application_date", java.time.LocalDate.class),
                            AspirantStatus.valueOf(rs.getString("status"))));
                }
            }
            return rows;
        }
    }

    /**
     * Cambia el estatus solo si sigue siendo {@code expected} (control optimista).
     * Devuelve vacío si otro usuario ya lo modificó o si el aspirante no existe.
     */
    public Optional<Transition> updateStatusIfCurrent(Connection conn, int idAspirant,
                                                      AspirantStatus expected, AspirantStatus target)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE aspirant_profile SET status = ?::aspirant_status
                WHERE id_aspirant = ? AND status = ?::aspirant_status
                RETURNING id_user, id_career
                """)) {
            ps.setString(1, target.name());
            ps.setInt(2, idAspirant);
            ps.setString(3, expected.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Transition(rs.getInt("id_user"), rs.getInt("id_career")))
                        : Optional.empty();
            }
        }
    }
}
