-- =====================================================================
-- schema.sql — full reset for the marketplace DB.
-- Drops every table (reverse FK order) + sequences, then recreates them.
-- Source of truth for the table DDL is data_design_report/create_tables.sql.
-- =====================================================================

-- ---- DROP TABLES (reverse FK order) --------------------------------
DROP TABLE EDEPOT_FILLED_ORDER_ITEM     CASCADE CONSTRAINTS PURGE;
DROP TABLE EDEPOT_FILLED_ORDER          CASCADE CONSTRAINTS PURGE;
DROP TABLE EDEPOT_REPL_ORDER_ITEM       CASCADE CONSTRAINTS PURGE;
DROP TABLE EDEPOT_REPL_ORDER            CASCADE CONSTRAINTS PURGE;
DROP TABLE EDEPOT_SHIPMENT              CASCADE CONSTRAINTS PURGE;
DROP TABLE EDEPOT_SHIPPING_NOTICE_ITEM  CASCADE CONSTRAINTS PURGE;
DROP TABLE EDEPOT_SHIPPING_NOTICE       CASCADE CONSTRAINTS PURGE;
DROP TABLE EDEPOT_INVENTORY_ITEM        CASCADE CONSTRAINTS PURGE;

DROP TABLE EMART_CART_ITEM              CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_CART                   CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_ORDER_ITEM             CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_ORDERS                 CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_SHIPPING_RULE          CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_DISCOUNT_RULE          CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_CUSTOMER               CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_COMPATIBILITY          CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_PRODUCT_ATTRIBUTE      CASCADE CONSTRAINTS PURGE;
DROP TABLE EMART_PRODUCT                CASCADE CONSTRAINTS PURGE;

-- ---- DROP SEQUENCES -------------------------------------------------
DROP SEQUENCE seq_emart_order;
DROP SEQUENCE seq_edepot_notice;
DROP SEQUENCE seq_edepot_shipment;
DROP SEQUENCE seq_edepot_repl_order;
DROP SEQUENCE seq_stock_number;

-- =====================================================================
-- eMART
-- =====================================================================
CREATE TABLE EMART_PRODUCT (
    stock_number    VARCHAR2(7)   NOT NULL,
    category        VARCHAR2(20)  NOT NULL,
    manufacturer    VARCHAR2(20)  NOT NULL,
    model_number    VARCHAR2(20)  NOT NULL,
    warranty_months NUMBER(4)     NOT NULL,
    price           NUMBER(10,2)  NOT NULL,
    CONSTRAINT pk_emart_product PRIMARY KEY (stock_number),
    CONSTRAINT ck_emart_product_stock_fmt
        CHECK (REGEXP_LIKE(stock_number, '^[A-Z]{2}[0-9]{5}$')),
    CONSTRAINT ck_emart_product_warranty CHECK (warranty_months >= 0),
    CONSTRAINT ck_emart_product_price    CHECK (price >= 0),
    CONSTRAINT uq_emart_product_mfr_model UNIQUE (manufacturer, model_number)
);

CREATE TABLE EMART_PRODUCT_ATTRIBUTE (
    stock_number  VARCHAR2(7)   NOT NULL,
    attr_name     VARCHAR2(20)  NOT NULL,
    attr_value    VARCHAR2(20)  NOT NULL,
    CONSTRAINT pk_emart_pa PRIMARY KEY (stock_number, attr_name),
    CONSTRAINT fk_emart_pa_product
        FOREIGN KEY (stock_number) REFERENCES EMART_PRODUCT(stock_number)
        ON DELETE CASCADE
);

CREATE TABLE EMART_COMPATIBILITY (
    product_stock      VARCHAR2(7) NOT NULL,
    can_replace_stock  VARCHAR2(7) NOT NULL,
    CONSTRAINT pk_emart_compat PRIMARY KEY (product_stock, can_replace_stock),
    CONSTRAINT fk_emart_compat_p
        FOREIGN KEY (product_stock) REFERENCES EMART_PRODUCT(stock_number)
        ON DELETE CASCADE,
    CONSTRAINT fk_emart_compat_r
        FOREIGN KEY (can_replace_stock) REFERENCES EMART_PRODUCT(stock_number)
        ON DELETE CASCADE,
    CONSTRAINT ck_emart_compat_not_self
        CHECK (product_stock <> can_replace_stock)
);

CREATE TABLE EMART_CUSTOMER (
    customer_id   VARCHAR2(20)  NOT NULL,
    password_hash VARCHAR2(64)  NOT NULL,
    first_name    VARCHAR2(20),
    middle_name   VARCHAR2(20),
    last_name     VARCHAR2(20),
    email         VARCHAR2(40)  NOT NULL,
    address       VARCHAR2(200) NOT NULL,
    status        VARCHAR2(10)  DEFAULT 'New' NOT NULL,
    CONSTRAINT pk_emart_customer PRIMARY KEY (customer_id),
    CONSTRAINT uq_emart_customer_email UNIQUE (email),
    CONSTRAINT ck_emart_customer_status
        CHECK (status IN ('Gold','Silver','Green','New'))
);

CREATE TABLE EMART_DISCOUNT_RULE (
    status_type    VARCHAR2(10)  NOT NULL,
    effective_date DATE          NOT NULL,
    discount_pct   NUMBER(5,2)   NOT NULL,
    CONSTRAINT pk_emart_discount PRIMARY KEY (status_type, effective_date),
    CONSTRAINT ck_emart_discount_status
        CHECK (status_type IN ('Gold','Silver','Green','New')),
    CONSTRAINT ck_emart_discount_pct
        CHECK (discount_pct >= 0 AND discount_pct <= 100)
);

CREATE TABLE EMART_SHIPPING_RULE (
    rule_name      VARCHAR2(20)  NOT NULL,
    effective_date DATE          NOT NULL,
    value          NUMBER(10,2)  NOT NULL,
    CONSTRAINT pk_emart_shipping PRIMARY KEY (rule_name, effective_date),
    CONSTRAINT ck_emart_shipping_value CHECK (value >= 0)
);

CREATE TABLE EMART_ORDERS (
    order_number          NUMBER(12)    NOT NULL,
    customer_id           VARCHAR2(20)  NOT NULL,
    order_date            DATE          DEFAULT SYSDATE NOT NULL,
    subtotal              NUMBER(12,2)  NOT NULL,
    discount_pct_applied  NUMBER(5,2)   NOT NULL,
    shipping_fee          NUMBER(10,2)  NOT NULL,
    total                 NUMBER(12,2)  NOT NULL,
    CONSTRAINT pk_emart_orders PRIMARY KEY (order_number),
    CONSTRAINT fk_emart_orders_cust
        FOREIGN KEY (customer_id) REFERENCES EMART_CUSTOMER(customer_id),
    CONSTRAINT ck_emart_orders_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_emart_orders_discount
        CHECK (discount_pct_applied >= 0 AND discount_pct_applied <= 100),
    CONSTRAINT ck_emart_orders_ship  CHECK (shipping_fee >= 0),
    CONSTRAINT ck_emart_orders_total CHECK (total >= 0)
);

CREATE TABLE EMART_ORDER_ITEM (
    order_number             NUMBER(12)   NOT NULL,
    stock_number             VARCHAR2(7)  NOT NULL,
    quantity                 NUMBER(8)    NOT NULL,
    unit_price_at_purchase   NUMBER(10,2) NOT NULL,
    CONSTRAINT pk_emart_oi PRIMARY KEY (order_number, stock_number),
    CONSTRAINT fk_emart_oi_order
        FOREIGN KEY (order_number) REFERENCES EMART_ORDERS(order_number)
        ON DELETE CASCADE,
    CONSTRAINT fk_emart_oi_product
        FOREIGN KEY (stock_number) REFERENCES EMART_PRODUCT(stock_number),
    CONSTRAINT ck_emart_oi_qty   CHECK (quantity > 0),
    CONSTRAINT ck_emart_oi_price CHECK (unit_price_at_purchase >= 0)
);

CREATE TABLE EMART_CART (
    customer_id   VARCHAR2(20) NOT NULL,
    last_updated  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_emart_cart PRIMARY KEY (customer_id),
    CONSTRAINT fk_emart_cart_cust
        FOREIGN KEY (customer_id) REFERENCES EMART_CUSTOMER(customer_id)
        ON DELETE CASCADE
);

CREATE TABLE EMART_CART_ITEM (
    customer_id   VARCHAR2(20) NOT NULL,
    stock_number  VARCHAR2(7)  NOT NULL,
    quantity      NUMBER(8)    NOT NULL,
    CONSTRAINT pk_emart_ci PRIMARY KEY (customer_id, stock_number),
    CONSTRAINT fk_emart_ci_cart
        FOREIGN KEY (customer_id) REFERENCES EMART_CART(customer_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_emart_ci_product
        FOREIGN KEY (stock_number) REFERENCES EMART_PRODUCT(stock_number),
    CONSTRAINT ck_emart_ci_qty CHECK (quantity > 0)
);


-- =====================================================================
-- eDEPOT
-- =====================================================================
CREATE TABLE EDEPOT_INVENTORY_ITEM (
    stock_number       VARCHAR2(7)  NOT NULL,
    manufacturer       VARCHAR2(20) NOT NULL,
    model_number       VARCHAR2(20) NOT NULL,
    quantity           NUMBER(8)    DEFAULT 0 NOT NULL,
    min_stock_level    NUMBER(8)    NOT NULL,
    max_stock_level    NUMBER(8)    NOT NULL,
    location           VARCHAR2(10) NOT NULL,
    replenishment_qty  NUMBER(8)    DEFAULT 0 NOT NULL,
    CONSTRAINT pk_edepot_inv PRIMARY KEY (stock_number),
    CONSTRAINT uq_edepot_inv_mfr_model UNIQUE (manufacturer, model_number),
    CONSTRAINT uq_edepot_inv_location  UNIQUE (location),
    CONSTRAINT ck_edepot_inv_stock_fmt
        CHECK (REGEXP_LIKE(stock_number, '^[A-Z]{2}[0-9]{5}$')),
    CONSTRAINT ck_edepot_inv_location_fmt
        CHECK (REGEXP_LIKE(UPPER(location), '^[A-Z][1-9][0-9]*$')),
    CONSTRAINT ck_edepot_inv_qty       CHECK (quantity >= 0),
    CONSTRAINT ck_edepot_inv_repl      CHECK (replenishment_qty >= 0),
    CONSTRAINT ck_edepot_inv_min_max   CHECK (min_stock_level <= max_stock_level),
    CONSTRAINT ck_edepot_inv_qty_max   CHECK (quantity <= max_stock_level)
);

CREATE TABLE EDEPOT_SHIPPING_NOTICE (
    notice_id         NUMBER(12)   NOT NULL,
    shipping_company  VARCHAR2(20) NOT NULL,
    received_date     DATE         DEFAULT SYSDATE NOT NULL,
    status            VARCHAR2(10) DEFAULT 'pending' NOT NULL,
    CONSTRAINT pk_edepot_notice PRIMARY KEY (notice_id),
    CONSTRAINT ck_edepot_notice_status
        CHECK (status IN ('pending','fulfilled'))
);

CREATE TABLE EDEPOT_SHIPPING_NOTICE_ITEM (
    notice_id      NUMBER(12)   NOT NULL,
    manufacturer   VARCHAR2(20) NOT NULL,
    model_number   VARCHAR2(20) NOT NULL,
    quantity       NUMBER(8)    NOT NULL,
    stock_number   VARCHAR2(7),
    CONSTRAINT pk_edepot_ni PRIMARY KEY (notice_id, manufacturer, model_number),
    CONSTRAINT fk_edepot_ni_notice
        FOREIGN KEY (notice_id) REFERENCES EDEPOT_SHIPPING_NOTICE(notice_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_edepot_ni_inv
        FOREIGN KEY (stock_number) REFERENCES EDEPOT_INVENTORY_ITEM(stock_number),
    CONSTRAINT ck_edepot_ni_qty CHECK (quantity > 0)
);

CREATE TABLE EDEPOT_SHIPMENT (
    shipment_id    NUMBER(12) NOT NULL,
    notice_id      NUMBER(12) NOT NULL,
    received_date  DATE       DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_edepot_ship PRIMARY KEY (shipment_id),
    CONSTRAINT fk_edepot_ship_notice
        FOREIGN KEY (notice_id) REFERENCES EDEPOT_SHIPPING_NOTICE(notice_id)
);

CREATE TABLE EDEPOT_REPL_ORDER (
    repl_order_id  NUMBER(12)   NOT NULL,
    manufacturer   VARCHAR2(20) NOT NULL,
    sent_date      DATE         DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_edepot_repl PRIMARY KEY (repl_order_id)
);

CREATE TABLE EDEPOT_REPL_ORDER_ITEM (
    repl_order_id  NUMBER(12)  NOT NULL,
    stock_number   VARCHAR2(7) NOT NULL,
    quantity       NUMBER(8)   NOT NULL,
    CONSTRAINT pk_edepot_ri PRIMARY KEY (repl_order_id, stock_number),
    CONSTRAINT fk_edepot_ri_order
        FOREIGN KEY (repl_order_id) REFERENCES EDEPOT_REPL_ORDER(repl_order_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_edepot_ri_inv
        FOREIGN KEY (stock_number) REFERENCES EDEPOT_INVENTORY_ITEM(stock_number),
    CONSTRAINT ck_edepot_ri_qty CHECK (quantity > 0)
);

CREATE TABLE EDEPOT_FILLED_ORDER (
    order_number  NUMBER(12) NOT NULL,
    filled_date   DATE       DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_edepot_fo PRIMARY KEY (order_number)
);

CREATE TABLE EDEPOT_FILLED_ORDER_ITEM (
    order_number  NUMBER(12)  NOT NULL,
    stock_number  VARCHAR2(7) NOT NULL,
    quantity      NUMBER(8)   NOT NULL,
    CONSTRAINT pk_edepot_foi PRIMARY KEY (order_number, stock_number),
    CONSTRAINT fk_edepot_foi_order
        FOREIGN KEY (order_number) REFERENCES EDEPOT_FILLED_ORDER(order_number)
        ON DELETE CASCADE,
    CONSTRAINT fk_edepot_foi_inv
        FOREIGN KEY (stock_number) REFERENCES EDEPOT_INVENTORY_ITEM(stock_number),
    CONSTRAINT ck_edepot_foi_qty CHECK (quantity > 0)
);


-- =====================================================================
-- Sequences for generated keys.
-- seq_stock_number is the raw counter; a Java helper formats N -> XXnnnnn.
-- =====================================================================
CREATE SEQUENCE seq_emart_order        START WITH 1000 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_edepot_notice      START WITH 1000 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_edepot_shipment    START WITH 1000 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_edepot_repl_order  START WITH 1000 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_stock_number       START WITH 1    INCREMENT BY 1 NOCACHE;
