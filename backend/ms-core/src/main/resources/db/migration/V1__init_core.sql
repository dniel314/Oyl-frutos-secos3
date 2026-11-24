-- =====================================================================
-- ms-core · esquema inicial en PostgreSQL (base exclusiva oyl_core)
-- Antes: H2 en memoria con ddl-auto=create-drop (datos perdidos al reiniciar).
-- Ahora: Flyway versiona el esquema y los datos persisten en el volumen.
-- Hibernate usa la estrategia de nombres por defecto (camelCase → snake_case).
-- =====================================================================

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    alias         VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    phone         VARCHAR(255) NOT NULL,
    address       VARCHAR(255),
    avatar_id     INTEGER,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(255) NOT NULL
);

CREATE TABLE category (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE product (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    category_id  BIGINT REFERENCES category (id),
    description  VARCHAR(1000) NOT NULL,
    price        BIGINT NOT NULL,
    presentation VARCHAR(255) NOT NULL,
    weight_grams INTEGER NOT NULL,
    origin       VARCHAR(255) NOT NULL,
    image_url    VARCHAR(255),
    stock        INTEGER NOT NULL,
    min_stock    INTEGER NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    qr_code      VARCHAR(255) UNIQUE,
    updated_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_product_category_id ON product (category_id);

CREATE TABLE orders (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users (id),
    order_number  VARCHAR(255) NOT NULL,
    status        VARCHAR(255) NOT NULL,
    subtotal      BIGINT NOT NULL,
    shipping      BIGINT NOT NULL,
    total         BIGINT NOT NULL,
    delivery_mode VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_orders_user_id ON orders (user_id);

CREATE TABLE order_item (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders (id),
    product_id BIGINT NOT NULL,
    name       VARCHAR(255) NOT NULL,
    unit_price BIGINT NOT NULL,
    quantity   INTEGER NOT NULL
);
CREATE INDEX idx_order_item_order_id ON order_item (order_id);

CREATE TABLE stock_movement (
    id              BIGSERIAL PRIMARY KEY,
    local_id        VARCHAR(255) NOT NULL UNIQUE,
    product_id      BIGINT NOT NULL,
    type            VARCHAR(255) NOT NULL,
    quantity        INTEGER NOT NULL,
    resulting_stock INTEGER NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE store (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    lat     DOUBLE PRECISION NOT NULL,
    lng     DOUBLE PRECISION NOT NULL,
    hours   VARCHAR(255) NOT NULL,
    type    VARCHAR(255) NOT NULL
);

CREATE TABLE notification (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    type         VARCHAR(255) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    message      VARCHAR(255) NOT NULL,
    target_route VARCHAR(255),
    target_id    BIGINT,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notification_user_id ON notification (user_id);

CREATE TABLE device_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
