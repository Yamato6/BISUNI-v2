package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.DashboardDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Dashboard;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;

/** Panel de inicio del admin: cifras del periodo activo y lo que requiere atención. */
public final class DashboardService {

    public static final int TOP_FAILING = 5;

    private final Database db;
    private final DashboardDao dashboard;

    public DashboardService(Database db, DashboardDao dashboard) {
        this.db = db;
        this.dashboard = dashboard;
    }

    public Dashboard load(UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            return db.withConnection(c -> dashboard.load(c, AttendanceService.RISK_PCT, TOP_FAILING));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el panel de inicio.", e);
        }
    }
}
