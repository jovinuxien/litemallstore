-- Create test tables
CREATE TABLE IF NOT EXISTS litemall_user (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(63) NOT NULL,
    password VARCHAR(63) NOT NULL DEFAULT '',
    --deleted TINYINT(1) DEFAULT 0
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS litemall_recommendation (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    recommendation_type VARCHAR(50) NOT NULL,
    algorithm_version VARCHAR(20) NOT NULL,
    confidence_score DECIMAL(5,4) DEFAULT 0.0000,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    context_page_type VARCHAR(50),
    context_device_type VARCHAR(50),
    context_user_segment VARCHAR(50),
    context_data TEXT,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    metadata CLOB,
    --deleted TINYINT(1) DEFAULT 0
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS litemall_recommendation_item (
    id INT PRIMARY KEY AUTO_INCREMENT,
    recommendation_id INT NOT NULL,
    goods_id INT NOT NULL,
    goods_name VARCHAR(255) NOT NULL,
    price DECIMAL(10,2),
    image_url VARCHAR(255),
    reason_code VARCHAR(50),
    reason_description TEXT,
    reason_data CLOB,
    confidence_score DECIMAL(5,4) DEFAULT 0.0000,
    recommended_at TIMESTAMP NOT NULL,
    clicked_at TIMESTAMP NULL,
    purchased_at TIMESTAMP NULL,
    click_count INT DEFAULT 0,
    --is_expired TINYINT(1) DEFAULT 0
    is_expired TINYINT DEFAULT 0
);

-- Insert test data
INSERT INTO litemall_user (id, username, password) VALUES
(1, 'testuser1', 'password1'),
(2, 'testuser2', 'password2');

-- Insert test recommendation data with proper timestamps
INSERT INTO litemall_recommendation (
    user_id, recommendation_type, algorithm_version,
    created_at, expires_at, updated_at
) VALUES
(1, 'PERSONALIZED', 'v1.0', CURRENT_TIMESTAMP,
 TIMESTAMPADD('DAY', 7, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP);