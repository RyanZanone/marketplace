package cs174a.ryanzanone.marketplace.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads /db/schema.sql off the classpath and executes it against the
 * configured database. DROP statements are tolerated on first run
 * (missing table / sequence) so the same script bootstraps an empty DB
 * and resets a populated one.
 *
 * Run with: mvn exec:java -Dexec.mainClass=cs174a.ryanzanone.marketplace.db.Schema
 */
public final class Schema {

    private static final String RESOURCE = "/db/schema.sql";

    // Oracle error codes we ignore for DROP statements on a fresh DB.
    private static final int ORA_TABLE_NOT_EXISTS    = 942;
    private static final int ORA_SEQUENCE_NOT_EXISTS = 2289;

    public static void main(String[] args) throws Exception {
        String script = loadScript();
        List<String> statements = splitStatements(script);

        System.out.println("Deploying " + statements.size() + " statements from " + RESOURCE);
        try (Connection conn = Db.get().getConnection();
             Statement stmt = conn.createStatement()) {
            int drops = 0, creates = 0, skipped = 0;
            for (String sql : statements) {
                String head = firstLine(sql);
                try {
                    stmt.execute(sql);
                    if (head.regionMatches(true, 0, "DROP", 0, 4)) drops++;
                    else creates++;
                    System.out.println("  ok    " + head);
                } catch (SQLException e) {
                    if (isIgnorableDropError(head, e)) {
                        skipped++;
                        System.out.println("  skip  " + head + "   (" + e.getErrorCode() + ")");
                    } else {
                        System.err.println("  FAIL  " + head);
                        throw e;
                    }
                }
            }
            System.out.println("Done. " + creates + " created, " + drops + " dropped, "
                + skipped + " skipped.");
        }
    }

    private static String loadScript() throws IOException {
        try (InputStream in = Schema.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("Resource " + RESOURCE + " not found on classpath");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder(8192);
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        }
    }

    /**
     * Splits a DDL-only script on top-level semicolons.
     * Strips full-line {@code --} comments. There are no string literals or
     * PL/SQL blocks in schema.sql, so a simple semicolon split is safe.
     */
    static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String raw : script.split("\\R")) {
            String trimmed = raw.stripLeading();
            if (trimmed.startsWith("--")) continue;     // drop comment lines
            if (raw.isBlank() && buf.length() == 0) continue;
            buf.append(raw).append('\n');
            // A statement terminates on a line whose last non-space char is ';'.
            String right = raw.stripTrailing();
            if (right.endsWith(";")) {
                String stmt = buf.toString();
                // strip the trailing ';' and any trailing whitespace
                int semi = stmt.lastIndexOf(';');
                stmt = stmt.substring(0, semi).strip();
                if (!stmt.isEmpty()) out.add(stmt);
                buf.setLength(0);
            }
        }
        String tail = buf.toString().strip();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static String firstLine(String sql) {
        int nl = sql.indexOf('\n');
        String head = (nl < 0 ? sql : sql.substring(0, nl)).strip();
        return head.length() > 80 ? head.substring(0, 77) + "..." : head;
    }

    private static boolean isIgnorableDropError(String head, SQLException e) {
        if (!head.regionMatches(true, 0, "DROP", 0, 4)) return false;
        int code = e.getErrorCode();
        return code == ORA_TABLE_NOT_EXISTS || code == ORA_SEQUENCE_NOT_EXISTS;
    }
}
