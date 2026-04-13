-- Initial Database Schema Creation
-- This creates all core tables for the Menu QR Generator application

-- Users table
CREATE TABLE IF NOT EXISTS simple_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    phone VARCHAR(20),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    restaurant_setup_complete BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_simple_users_email ON simple_users(email);

-- Restaurants table
CREATE TABLE IF NOT EXISTS restaurants (
    id BIGSERIAL PRIMARY KEY,
    restaurant_name VARCHAR(255) NOT NULL,
    restaurant_type VARCHAR(100) NOT NULL,
    logo_path VARCHAR(500),
    menu_background_color VARCHAR(7),
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_restaurants_user FOREIGN KEY (user_id) REFERENCES simple_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_restaurants_user_id ON restaurants(user_id);

-- Branches table
CREATE TABLE IF NOT EXISTS branches (
    id BIGSERIAL PRIMARY KEY,
    branch_name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    is_main_branch BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    restaurant_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_branches_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_branches_restaurant_id ON branches(restaurant_id);

-- Menu Items table
CREATE TABLE IF NOT EXISTS menu_items (
    id BIGSERIAL PRIMARY KEY,
    item_name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10,2),
    photo_path VARCHAR(500),
    is_available BOOLEAN NOT NULL DEFAULT true,
    restaurant_id BIGINT NOT NULL,
    category_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_menu_items_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_menu_items_restaurant_id ON menu_items(restaurant_id);

-- Branch Menu Items table (for branch-specific menu items)
CREATE TABLE IF NOT EXISTS branch_menu_items (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10,2),
    photo_path VARCHAR(500),
    category VARCHAR(100),
    hidden BOOLEAN NOT NULL DEFAULT false,
    parent_item_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_branch_menu_items_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    CONSTRAINT fk_branch_menu_items_parent FOREIGN KEY (parent_item_id) REFERENCES branch_menu_items(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_branch_menu_items_branch_id ON branch_menu_items(branch_id);
