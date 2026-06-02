package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class StockNumberGenerator {

    private static final String NEXTVAL_SQL = "SELECT seq_stock_number.NEXTVAL FROM dual";
    private static final long MAX_DIGITS = 99_999L;

    private StockNumberGenerator() {}
    
    public static String next(Connection conn, String category) throws SQLException {
        long n = nextSeq(conn);
        if (n > MAX_DIGITS) {
            throw new IllegalStateException(
                "seq_stock_number exceeded " + MAX_DIGITS
                    + "; the XXnnnnn format can no longer be satisfied. Got n=" + n);
        }
        return prefixFor(category) + String.format("%05d", n);
    }

    static String prefixFor(String category) {
        StringBuilder letters = new StringBuilder(2);
        if (category != null) {
            for (int i = 0; i < category.length() && letters.length() < 2; i++) {
                char c = category.charAt(i);
                if (Character.isLetter(c)) letters.append(Character.toUpperCase(c));
            }
        }
        while (letters.length() < 2) letters.append('X');
        return letters.toString();
    }

    private static long nextSeq(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(NEXTVAL_SQL)) {
            if (!rs.next()) {
                throw new SQLException("seq_stock_number.NEXTVAL returned no row");
            }
            return rs.getLong(1);
        }
    }
}
