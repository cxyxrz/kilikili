-- MySQL 8 existing-database migration for the admin user-management module.
-- This script is idempotent and can be executed repeatedly.

SET @schema_name = DATABASE();

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'account' AND COLUMN_NAME = 'email') = 0,
  'ALTER TABLE account ADD COLUMN email VARCHAR(128) NULL AFTER username',
  'SELECT 1'
);
PREPARE user_stmt FROM @statement;
EXECUTE user_stmt;
DEALLOCATE PREPARE user_stmt;

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'account' AND COLUMN_NAME = 'last_login_at') = 0,
  'ALTER TABLE account ADD COLUMN last_login_at DATETIME NULL AFTER role',
  'SELECT 1'
);
PREPARE user_stmt FROM @statement;
EXECUTE user_stmt;
DEALLOCATE PREPARE user_stmt;

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'account' AND INDEX_NAME = 'uk_account_email') = 0,
  'ALTER TABLE account ADD UNIQUE INDEX uk_account_email (email)',
  'SELECT 1'
);
PREPARE user_stmt FROM @statement;
EXECUTE user_stmt;
DEALLOCATE PREPARE user_stmt;

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'account' AND INDEX_NAME = 'idx_account_role_status_created') = 0,
  'ALTER TABLE account ADD INDEX idx_account_role_status_created (role, status, created_at)',
  'SELECT 1'
);
PREPARE user_stmt FROM @statement;
EXECUTE user_stmt;
DEALLOCATE PREPARE user_stmt;
