package net.rustcore.duel.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DaoSupport {

    public interface SqlFunction<T> { T apply(Connection c) throws SQLException; }
    public interface RowMapper<T>   { T map(ResultSet rs) throws SQLException; }
    public interface StmtBinder     { void bind(PreparedStatement ps) throws SQLException; }

    private final DataSource ds;
    private final boolean mysql;

    public DaoSupport(DataSource ds) {
        this.ds = ds;
        this.mysql = computeIsMySql(ds);
    }

    private static boolean computeIsMySql(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            return c.getMetaData().getURL().startsWith("jdbc:mysql");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T withConnection(SqlFunction<T> fn) {
        try (Connection c = ds.getConnection()) { return fn.apply(c); }
        catch (SQLException e) { throw new RuntimeException(e); }
    }

    public <T> T withTx(SqlFunction<T> fn) {
        try (Connection c = ds.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T out = fn.apply(c);
                c.commit();
                return out;
            } catch (Throwable t) {
                c.rollback();
                throw t;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public <T> List<T> queryList(String sql, StmtBinder b, RowMapper<T> m) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (b != null) b.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> out = new ArrayList<>();
                    while (rs.next()) out.add(m.map(rs));
                    return out;
                }
            }
        });
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> m) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(m.map(rs)) : Optional.<T>empty();
            }
        });
    }

    public int execute(String sql, StmtBinder b) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (b != null) b.bind(ps);
                return ps.executeUpdate();
            }
        });
    }

    /** True when the JDBC URL of the pool is a MySQL URL. H2 callers should use portable MERGE. */
    public boolean isMySql() { return mysql; }
}
