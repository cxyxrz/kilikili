-- category 表升级脚本（MySQL 8 兼容）
-- 为 category 表补充 code、parent_id 字段，并迁移已有数据
-- 用法: mysql -u root -p kilikili < migrate_category_v2.sql

-- 1. 添加 code 列（如果不存在）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'category' AND column_name = 'code');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE category ADD COLUMN code VARCHAR(64) NULL',
  'SELECT ''code column already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 添加 parent_id 列（如果不存在）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'category' AND column_name = 'parent_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE category ADD COLUMN parent_id BIGINT NULL',
  'SELECT ''parent_id column already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 添加索引（如果不存在）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'category' AND index_name = 'idx_category_parent_sort');
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE category ADD INDEX idx_category_parent_sort (parent_id, sort_order)',
  'SELECT ''index already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 让 code 列唯一且非空（先填充数据再改约束）
UPDATE category SET code = CASE id
  WHEN 1 THEN 'anime' WHEN 2 THEN 'mad-amv' WHEN 3 THEN 'mmd-3d' WHEN 4 THEN 'dubbing'
  WHEN 5 THEN 'music' WHEN 6 THEN 'game' WHEN 7 THEN 'game-live' WHEN 8 THEN 'e-sports'
  WHEN 9 THEN 'tech' WHEN 10 THEN 'programming'
  ELSE CONCAT('category-', id)
END WHERE code IS NULL OR code = '';

UPDATE category SET parent_id = 1, sort_order = id - 1 WHERE id IN (2, 3, 4) AND parent_id IS NULL;
UPDATE category SET parent_id = 6, sort_order = id - 6 WHERE id IN (7, 8) AND parent_id IS NULL;
UPDATE category SET parent_id = 9, sort_order = 1 WHERE id = 10 AND parent_id IS NULL;

-- 5. 补充数码评测子分类
INSERT IGNORE INTO category(name, code, parent_id, sort_order)
SELECT '数码评测', 'review', id, 2 FROM category WHERE code = 'tech';

-- 6. 收紧约束
ALTER TABLE category MODIFY COLUMN code VARCHAR(64) NOT NULL;
SET @uk_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'category' AND index_name = 'uk_category_code');
SET @sql := IF(@uk_exists = 0,
  'ALTER TABLE category ADD UNIQUE KEY uk_category_code (code)',
  'SELECT ''unique key already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT id, name, code, parent_id, sort_order, status FROM category ORDER BY parent_id, sort_order, id;