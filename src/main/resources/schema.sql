CREATE TABLE IF NOT EXISTS items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_code VARCHAR(30) NOT NULL UNIQUE,
    item_name VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL,
    item_size VARCHAR(30),
    daily_rate DECIMAL(10,2) NOT NULL CHECK (daily_rate > 0)
);

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    pickup_date DATE NOT NULL,
    return_date DATE NOT NULL,
    CHECK (return_date >= pickup_date),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE IF NOT EXISTS booking_items (
    booking_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    daily_rate DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (booking_id, item_id),
    INDEX (item_id),
    FOREIGN KEY (booking_id) REFERENCES bookings(id),
    FOREIGN KEY (item_id) REFERENCES items(id)
);

CREATE TABLE IF NOT EXISTS item_returns (
    booking_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    returned_on DATE NOT NULL,
    PRIMARY KEY (booking_id, item_id),
    FOREIGN KEY (booking_id, item_id)
        REFERENCES booking_items(booking_id, item_id)
);
