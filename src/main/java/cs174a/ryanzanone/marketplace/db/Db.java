package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.pool.OracleDataSource;

public final class Db {

    private static volatile Db instance;

    private final OracleDataSource ds;

    private Db(OracleDataSource ds) {
        this.ds = ds;
    }

    public static Db get() {
        Db local = instance;
        if (local != null) return local;
        synchronized (Db.class) {
            if (instance == null) instance = build(DbConfig.load());
            return instance;
        }
    }

    private static Db build(DbConfig cfg) {
        try {
            OracleDataSource ds = new OracleDataSource();
            ds.setURL(cfg.url());
            Properties info = new Properties();
            info.put(OracleConnection.CONNECTION_PROPERTY_USER_NAME, cfg.user());
            info.put(OracleConnection.CONNECTION_PROPERTY_PASSWORD, cfg.password());
            info.put(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "20");
            ds.setConnectionProperties(info);
            return new Db(ds);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize OracleDataSource", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public <T> T withTx(TxBlock<T> block) {
        try (Connection conn = ds.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = block.run(conn);
                conn.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                safeRollback(conn);
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            } finally {
                try { conn.setAutoCommit(prevAutoCommit); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to acquire connection", e);
        }
    }

    private static void safeRollback(Connection conn) {
        try { conn.rollback(); } catch (SQLException ignored) {}
    }

    @FunctionalInterface
    public interface TxBlock<T> {
        T run(Connection conn) throws SQLException;
    }
}
