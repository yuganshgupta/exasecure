package com.examsystem.server;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;

/** Faults/barriers at real JDBC boundaries, with real MySQL rollback and locks. */
final class JdbcTestProbe {
    interface Probe { void at(String sql, boolean after) throws Exception; }
    static DataSource wrap(DataSource source, Probe probe) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
            if (method.getName().equals("getConnection")) probe.at("CONNECT", false);
            Object result = invoke(source, method, args);
            if (result instanceof Connection conn) return connection(conn, probe);
            return result;
        });
    }
    private static Connection connection(Connection conn, Probe probe) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("commit")) probe.at("COMMIT", false);
            Object result = invoke(conn, method, args);
            if (result instanceof Statement stmt) {
                String sql = args != null && args.length > 0 && args[0] instanceof String ? (String) args[0] : null;
                Class<?> kind = stmt instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                return Proxy.newProxyInstance(kind.getClassLoader(), new Class<?>[]{kind}, (p, m, a) -> {
                    String command = sql != null ? sql : a != null && a.length > 0 && a[0] instanceof String ? (String) a[0] : "";
                    boolean execute = m.getName().startsWith("execute");
                    if (execute) probe.at(command, false);
                    Object value = invoke(stmt, m, a);
                    if (execute) probe.at(command, true);
                    return value;
                });
            }
            return result;
        });
    }
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
}
