package cs174a.ryanzanone.marketplace.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cs174a.ryanzanone.marketplace.db.model.Compatibility;
import cs174a.ryanzanone.marketplace.db.model.Product;
import cs174a.ryanzanone.marketplace.db.model.ProductAttribute;
import cs174a.ryanzanone.marketplace.db.model.ProductSearchCriteria;

/**
 * DAO for the eMART catalog tables:
 * {@code EMART_PRODUCT}, {@code EMART_PRODUCT_ATTRIBUTE},
 * {@code EMART_COMPATIBILITY}.
 *
 * <p>Backs the customer search operations from Project Description.txt §2.3
 * and the manager catalog edits from §2.4 ("change the price of an item").
 * See {@code backup.tex} §3.1 for the operation→column trace.
 *
 * <p>All methods require an open {@link Connection}. None open or close
 * transactions themselves — callers wrap them with {@link Db#withTx(Db.TxBlock)}.
 */
public final class ProductDao {

    private static final String SELECT_COLS =
        "stock_number, category, manufacturer, model_number, warranty_months, price";

    private ProductDao() {}

    /** Returns the product, if any, with the given stock number. */
    public static Optional<Product> findByStock(Connection conn, String stockNumber)
            throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EMART_PRODUCT WHERE stock_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readProduct(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Catalog search. Non-null fields on {@code criteria} are AND'd into a
     * single WHERE clause; joins to {@code EMART_PRODUCT_ATTRIBUTE} and
     * {@code EMART_COMPATIBILITY} are added only when those criteria are set.
     * "compatible with X" returns the products that can replace X (see
     * {@code ProductSearchCriteria} javadoc).
     */
    public static List<Product> search(Connection conn, ProductSearchCriteria criteria)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT p.stock_number, p.category, p.manufacturer, p.model_number, "
                + "p.warranty_months, p.price FROM EMART_PRODUCT p");
        List<Object> params = new ArrayList<>();
        List<String> wheres = new ArrayList<>();

        if (criteria.attrName() != null) {
            sql.append(" JOIN EMART_PRODUCT_ATTRIBUTE pa ON pa.stock_number = p.stock_number");
            wheres.add("pa.attr_name = ?");
            params.add(criteria.attrName());
            if (criteria.attrValue() != null) {
                wheres.add("pa.attr_value = ?");
                params.add(criteria.attrValue());
            }
        }
        if (criteria.compatibleWithStock() != null) {
            // Customer-facing semantics from spec §2.3 ("search for compatible
            // items of an item"): find products P such that P can replace the
            // given stock number X. That's rows of EMART_COMPATIBILITY where
            // can_replace_stock = X, joined to EMART_PRODUCT on product_stock.
            sql.append(" JOIN EMART_COMPATIBILITY c ON c.product_stock = p.stock_number");
            wheres.add("c.can_replace_stock = ?");
            params.add(criteria.compatibleWithStock());
        }
        if (criteria.stockNumber() != null) {
            wheres.add("p.stock_number = ?");
            params.add(criteria.stockNumber());
        }
        if (criteria.manufacturer() != null) {
            wheres.add("p.manufacturer = ?");
            params.add(criteria.manufacturer());
        }
        if (criteria.modelNumber() != null) {
            wheres.add("p.model_number = ?");
            params.add(criteria.modelNumber());
        }
        if (criteria.category() != null) {
            wheres.add("p.category = ?");
            params.add(criteria.category());
        }

        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", wheres));
        }
        sql.append(" ORDER BY p.stock_number");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Product> out = new ArrayList<>();
                while (rs.next()) out.add(readProduct(rs));
                return out;
            }
        }
    }

    /** All {@code EMART_PRODUCT_ATTRIBUTE} rows for the given stock number. */
    public static List<ProductAttribute> attributesOf(Connection conn, String stockNumber)
            throws SQLException {
        String sql = "SELECT stock_number, attr_name, attr_value FROM EMART_PRODUCT_ATTRIBUTE "
            + "WHERE stock_number = ? ORDER BY attr_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockNumber);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductAttribute> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new ProductAttribute(
                        rs.getString("stock_number"),
                        rs.getString("attr_name"),
                        rs.getString("attr_value")));
                }
                return out;
            }
        }
    }

    /**
     * Stock numbers of products that {@code stockNumber} can be replaced by
     * (i.e. {@code can_replace_stock} where {@code product_stock = stockNumber}).
     */
    public static List<String> compatibleWith(Connection conn, String stockNumber)
            throws SQLException {
        String sql = "SELECT can_replace_stock FROM EMART_COMPATIBILITY "
            + "WHERE product_stock = ? ORDER BY can_replace_stock";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockNumber);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getString(1));
                return out;
            }
        }
    }

    /**
     * Inserts a product plus its attributes and compatibility rows. Caller
     * wraps in {@link Db#withTx(Db.TxBlock)} for atomicity. {@code attributes}
     * and {@code compatibilities} may be {@code null} or empty.
     */
    public static void insert(Connection conn,
                              Product product,
                              List<ProductAttribute> attributes,
                              List<Compatibility> compatibilities) throws SQLException {
        String insertProd = "INSERT INTO EMART_PRODUCT "
            + "(stock_number, category, manufacturer, model_number, warranty_months, price) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertProd)) {
            ps.setString(1, product.stockNumber());
            ps.setString(2, product.category());
            ps.setString(3, product.manufacturer());
            ps.setString(4, product.modelNumber());
            ps.setInt(5, product.warrantyMonths());
            ps.setBigDecimal(6, product.price());
            ps.executeUpdate();
        }
        if (attributes != null && !attributes.isEmpty()) {
            String insertAttr = "INSERT INTO EMART_PRODUCT_ATTRIBUTE "
                + "(stock_number, attr_name, attr_value) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertAttr)) {
                for (ProductAttribute a : attributes) {
                    ps.setString(1, product.stockNumber());
                    ps.setString(2, a.name());
                    ps.setString(3, a.value());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        if (compatibilities != null && !compatibilities.isEmpty()) {
            String insertCompat = "INSERT INTO EMART_COMPATIBILITY "
                + "(product_stock, can_replace_stock) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertCompat)) {
                for (Compatibility c : compatibilities) {
                    ps.setString(1, c.productStock());
                    ps.setString(2, c.canReplaceStock());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    /** Manager flow from Project Description.txt §2.4. Returns rows affected (0 or 1). */
    public static int updatePrice(Connection conn, String stockNumber, BigDecimal newPrice)
            throws SQLException {
        String sql = "UPDATE EMART_PRODUCT SET price = ? WHERE stock_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice);
            ps.setString(2, stockNumber);
            return ps.executeUpdate();
        }
    }

    /**
     * Deletes a product. Will fail with FK violation if the product is in
     * any cart or order ({@code backup.tex} §4.3). Caller surfaces a friendly
     * error.
     */
    public static int delete(Connection conn, String stockNumber) throws SQLException {
        String sql = "DELETE FROM EMART_PRODUCT WHERE stock_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockNumber);
            return ps.executeUpdate();
        }
    }

    private static Product readProduct(ResultSet rs) throws SQLException {
        return new Product(
            rs.getString("stock_number"),
            rs.getString("category"),
            rs.getString("manufacturer"),
            rs.getString("model_number"),
            rs.getInt("warranty_months"),
            rs.getBigDecimal("price"));
    }
}
