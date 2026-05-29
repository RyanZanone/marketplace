package cs174a.ryanzanone.marketplace.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DbConfig {

    private final String url;
    private final String user;
    private final String password;

    private DbConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public String url() { return url; }
    public String user() { return user; }
    public String password() { return password; }

    public static DbConfig load() {
        Properties props = new Properties();
        loadFromClasspath(props);
        loadFromUserFile(props);

        String url = resolve(props, "db.url", "DB_URL");
        String user = resolve(props, "db.user", "DB_USER");
        String password = resolve(props, "db.password", "DB_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new IllegalStateException(
                "Missing DB config. Provide db.properties on the classpath, " +
                "~/.marketplace/db.properties, or DB_URL / DB_USER / DB_PASSWORD env vars.");
        }
        return new DbConfig(url, user, password);
    }

    private static void loadFromClasspath(Properties props) {
        try (InputStream in = DbConfig.class.getResourceAsStream("/db.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {
        }
    }

    private static void loadFromUserFile(Properties props) {
        Path p = Path.of(System.getProperty("user.home"), ".marketplace", "db.properties");
        if (!Files.isReadable(p)) return;
        try (InputStream in = Files.newInputStream(p)) {
            props.load(in);
        } catch (IOException ignored) {
        }
    }

    private static String resolve(Properties props, String propKey, String envKey) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v.trim();
        v = props.getProperty(propKey);
        if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
