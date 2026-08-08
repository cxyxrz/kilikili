-- MySQL 8 existing-database migration for two-level category management.
-- This script is idempotent and can be executed repeatedly.

SET @schema_name = DATABASE();

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'category' AND COLUMN_NAME = 'code') = 0,
  'ALTER TABLE category ADD COLUMN code VARCHAR(64) NULL AFTER name',
  'SELECT 1'
);
PREPARE category_stmt FROM @statement;
EXECUTE category_stmt;
DEALLOCATE PREPARE category_stmt;

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'category' AND COLUMN_NAME = 'parent_id') = 0,
  'ALTER TABLE category ADD COLUMN parent_id BIGINT NULL AFTER code',
  'SELECT 1'
);
PREPARE category_stmt FROM @statement;
EXECUTE category_stmt;
DEALLOCATE PREPARE category_stmt;

ALTER TABLE category MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

UPDATE category SET code = CASE id
  WHEN 1 THEN 'anime' WHEN 2 THEN 'mad-amv' WHEN 3 THEN 'mmd-3d' WHEN 4 THEN 'dubbing'
  WHEN 5 THEN 'music' WHEN 6 THEN 'game' WHEN 7 THEN 'game-live' WHEN 8 THEN 'e-sports'
  WHEN 9 THEN 'tech' WHEN 10 THEN 'programming'
  ELSE CONCAT('category-', id)
END WHERE code IS NULL OR code = '';

UPDATE category SET sort_order = CASE id
  WHEN 1 THEN 1 WHEN 5 THEN 2 WHEN 6 THEN 3 WHEN 9 THEN 4
  ELSE sort_order
END
WHERE (id = 1 AND sort_order = 10)
   OR (id = 5 AND sort_order = 50)
   OR (id = 6 AND sort_order = 60)
   OR (id = 9 AND sort_order = 90);

UPDATE category SET parent_id = 1, sort_order = id - 1 WHERE id IN (2, 3, 4) AND parent_id IS NULL;
UPDATE category SET parent_id = 6, sort_order = id - 6 WHERE id IN (7, 8) AND parent_id IS NULL;
UPDATE category SET parent_id = 9, sort_order = 1 WHERE id = 10 AND parent_id IS NULL;

ALTER TABLE category MODIFY COLUMN code VARCHAR(64) NOT NULL;

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'category' AND INDEX_NAME = 'uk_category_code') = 0,
  'ALTER TABLE category ADD UNIQUE INDEX uk_category_code (code)',
  'SELECT 1'
);
PREPARE category_stmt FROM @statement;
EXECUTE category_stmt;
DEALLOCATE PREPARE category_stmt;

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'category' AND INDEX_NAME = 'idx_category_parent_sort') = 0,
  'ALTER TABLE category ADD INDEX idx_category_parent_sort (parent_id, sort_order)',
  'SELECT 1'
);
PREPARE category_stmt FROM @statement;
EXECUTE category_stmt;
DEALLOCATE PREPARE category_stmt;

INSERT IGNORE INTO category(name, code, parent_id, sort_order)
SELECT '数码评测', 'review', id, 2 FROM category WHERE code = 'tech';
INSERT IGNORE INTO category(name, code, parent_id, sort_order)
SELECT '人工智能', 'ai', id, 3 FROM category WHERE code = 'tech';
INSERT IGNORE INTO category(name, code, parent_id, sort_order) VALUES
  ('生活', 'life', NULL, 5), ('美食', 'food', NULL, 6), ('知识', 'knowledge', NULL, 7),
  ('运动', 'sports', NULL, 8), ('时尚', 'fashion', NULL, 9), ('娱乐', 'entertainment', NULL, 10),
  ('影视', 'film', NULL, 11), ('舞蹈', 'dance', NULL, 12);
