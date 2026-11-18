package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.AnnouncementAudience;
import mx.bisuni.desktop.model.AnnouncementData;
import mx.bisuni.desktop.model.AnnouncementRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AnnouncementDao {

    private static final String SELECT = """
            SELECT a.id_announcement, a.title, a.body, a.audience::text AS audience,
                   coalesce(c.career_name, g.group_name || ' (' || p.name || ')') AS target,
                   a.author_user, u.first_name || ' ' || u.last_name AS author_name,
                   a.published_at, a.expires_at
            FROM announcement a
            JOIN users u ON u.id_user = a.author_user
            LEFT JOIN career c ON c.id_career = a.id_career
            LEFT JOIN school_groups g ON g.id_group = a.id_group
            LEFT JOIN academic_period p ON p.id_period = g.id_period
            """;

    /** Avisos, del más reciente al más antiguo. {@code authorUser == null} = de todos los autores. */
    public List<AnnouncementRow> list(Connection conn, Integer authorUser) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT
                + " WHERE (?::int IS NULL OR a.author_user = ?) ORDER BY a.published_at DESC, a.id_announcement DESC")) {
            StudentDao.setNullableInt(ps, 1, authorUser);
            StudentDao.setNullableInt(ps, 2, authorUser);
            List<AnnouncementRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        }
    }

    public Optional<AnnouncementRow> findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE a.id_announcement = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int insert(Connection conn, AnnouncementData d, int authorUser, OffsetDateTime expiresAt)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO announcement (title, body, audience, id_career, id_group, author_user, expires_at)
                VALUES (?, ?, ?::announcement_audience, ?, ?, ?, ?) RETURNING id_announcement
                """)) {
            ps.setString(1, d.title());
            ps.setString(2, d.body());
            ps.setString(3, d.audience().name());
            StudentDao.setNullableInt(ps, 4, d.idCareer());
            StudentDao.setNullableInt(ps, 5, d.idGroup());
            ps.setInt(6, authorUser);
            if (expiresAt == null) {
                ps.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setObject(7, expiresAt);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean delete(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM announcement WHERE id_announcement = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static AnnouncementRow map(ResultSet rs) throws SQLException {
        return new AnnouncementRow(
                rs.getInt("id_announcement"),
                rs.getString("title"),
                rs.getString("body"),
                AnnouncementAudience.valueOf(rs.getString("audience")),
                rs.getString("target"),
                rs.getInt("author_user"),
                rs.getString("author_name"),
                rs.getObject("published_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class));
    }
}
