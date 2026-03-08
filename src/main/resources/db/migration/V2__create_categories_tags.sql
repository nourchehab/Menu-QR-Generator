-- Migration: create categories, tags, and item_tags join table
-- Adjust names/types for your existing schema where necessary

CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_categories_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_restaurant_name ON categories (restaurant_id, lower(name));

CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS item_tags (
    item_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (item_id, tag_id),
    CONSTRAINT fk_itemtags_item FOREIGN KEY (item_id) REFERENCES menu_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_itemtags_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

-- If your restaurants or menu_items table names differ, adjust the FK references accordingly.
