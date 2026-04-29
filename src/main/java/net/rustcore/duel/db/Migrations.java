package net.rustcore.duel.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

public final class Migrations {

    private static final Pattern NAME = Pattern.compile("V(\\d+)__([A-Za-z0-9_\\-]+)\\.sql");

    private final DataSource ds;

    public Migrations(DataSource ds) { this.ds = ds; }

    public void apply() throws Exception {
        try (Connection c = ds.getConnection()) {
            ensureVersionTable(c);
            List<Script> scripts = discover();
            Collections.sort(scripts, (a, b) -> Integer.compare(a.version, b.version));
            for (Script s : scripts) {
                if (alreadyApplied(c, s.version)) continue;
                runSql(c, s.sql);
                record(c, s.version, s.description);
            }
        }
    }

    private void ensureVersionTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (" +
                    "version INT NOT NULL PRIMARY KEY," +
                    "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "description VARCHAR(255) NOT NULL)");
        }
    }

    private boolean alreadyApplied(Connection c, int v) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM schema_version WHERE version = ?")) {
            ps.setInt(1, v);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private void record(Connection c, int v, String desc) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schema_version(version, description) VALUES (?, ?)")) {
            ps.setInt(1, v);
            ps.setString(2, desc);
            ps.executeUpdate();
        }
    }

    private void runSql(Connection c, String sql) throws Exception {
        for (String stmt : sql.split(";\\s*\\r?\\n")) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            try (Statement st = c.createStatement()) { 
                st.executeUpdate(trimmed); 
            } catch (java.sql.SQLException e) {
                // Ignore error 1091: ER_CANT_DROP_FIELD_OR_KEY (Can't DROP '...'; check that column/key exists)
                if (e.getErrorCode() == 1091) {
                    System.out.println("[RustCore-Duels] Ignoring expected DB error: " + e.getMessage());
                    continue;
                }
                throw e;
            }
        }
    }

    private List<Script> discover() throws Exception {
        List<Script> out = new ArrayList<>();
        ClassLoader cl = Migrations.class.getClassLoader();
        Enumeration<URL> roots = cl.getResources("db/migrations");
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if ("file".equals(root.getProtocol())) {
                Path dir = Paths.get(root.toURI());
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(Files::isRegularFile)
                            .forEach(path -> addScript(out, cl, "db/migrations/" + path.getFileName()));
                }
            } else if ("jar".equals(root.getProtocol())) {
                JarURLConnection conn = (JarURLConnection) root.openConnection();
                String dir = conn.getEntryName();
                try (JarFile jar = conn.getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!entry.isDirectory() && name.startsWith(dir + "/")) {
                            addScript(out, cl, name);
                        }
                    }
                }
            }
        }
        return out;
    }

    private void addScript(List<Script> out, ClassLoader cl, String resourceName) {
        String fileName = resourceName.substring(resourceName.lastIndexOf('/') + 1);
        Matcher m = NAME.matcher(fileName);
        if (!m.matches()) return;

        try (InputStream is = cl.getResourceAsStream(resourceName)) {
            if (is == null) return;
            out.add(new Script(Integer.parseInt(m.group(1)), m.group(2), read(is)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load migration " + resourceName, ex);
        }
    }

    private String read(InputStream in) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private record Script(int version, String description, String sql) {}
}
