package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.CatalogDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Catalog;

import java.sql.SQLException;
import java.util.List;

/** Catálogos (carreras, grupos, materias) para combos y filtros. */
public final class CatalogService {

    private final Database db;
    private final CatalogDao catalog;

    public CatalogService(Database db, CatalogDao catalog) {
        this.db = db;
        this.catalog = catalog;
    }

    public List<Catalog.Career> careers() throws ServiceException {
        try {
            return db.withConnection(catalog::activeCareers);
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el catálogo de carreras.", e);
        }
    }

    public List<Catalog.Group> groups() throws ServiceException {
        try {
            return db.withConnection(catalog::activeGroups);
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el catálogo de grupos.", e);
        }
    }

    public List<Catalog.Subject> subjects() throws ServiceException {
        try {
            return db.withConnection(catalog::activeSubjects);
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el catálogo de materias.", e);
        }
    }
}
