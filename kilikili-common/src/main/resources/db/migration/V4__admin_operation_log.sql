CREATE TABLE IF NOT EXISTS admin_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NOT NULL,
  module VARCHAR(32) NOT NULL,
  action VARCHAR(16) NOT NULL,
  description VARCHAR(255) NOT NULL,
  ip_address VARCHAR(64) NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(500) NULL,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_log_created (created_at),
  INDEX idx_admin_log_module_created (module, created_at),
  INDEX idx_admin_log_operator_created (operator_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
