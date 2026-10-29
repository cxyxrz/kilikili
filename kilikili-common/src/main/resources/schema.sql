CREATE TABLE IF NOT EXISTS account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  email VARCHAR(128) NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  avatar VARCHAR(500),
  gender VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
  birthday DATE,
  bio VARCHAR(200),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  author_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(2000),
  object_key VARCHAR(500) NOT NULL,
  cover_url VARCHAR(500),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  view_count BIGINT NOT NULL DEFAULT 0,
  like_count BIGINT NOT NULL DEFAULT 0,
  comment_count BIGINT NOT NULL DEFAULT 0,
  category_id BIGINT NOT NULL DEFAULT 1,
  cover_object_key VARCHAR(500),
  duration_seconds INT NOT NULL DEFAULT 0,
  creation_declaration VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_video_status_created (status, created_at),
  INDEX idx_video_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE,
  code VARCHAR(64) NOT NULL UNIQUE,
  parent_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_category_parent_sort (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO category(id, name, code, parent_id, sort_order) VALUES
  (1, '动画', 'anime', NULL, 1), (2, 'MAD/AMV', 'mad-amv', 1, 1),
  (3, 'MMD/3D', 'mmd-3d', 1, 2), (4, '配音', 'dubbing', 1, 3),
  (5, '音乐', 'music', NULL, 2), (6, '游戏', 'game', NULL, 3),
  (7, '游戏直播', 'game-live', 6, 1), (8, '电子竞技', 'e-sports', 6, 2),
  (9, '科技', 'tech', NULL, 4), (10, '编程', 'programming', 9, 1);

UPDATE category SET code = CASE id
  WHEN 1 THEN 'anime' WHEN 2 THEN 'mad-amv' WHEN 3 THEN 'mmd-3d' WHEN 4 THEN 'dubbing'
  WHEN 5 THEN 'music' WHEN 6 THEN 'game' WHEN 7 THEN 'game-live' WHEN 8 THEN 'e-sports'
  WHEN 9 THEN 'tech' WHEN 10 THEN 'programming'
  ELSE CONCAT('category-', id)
END WHERE code IS NULL OR code = '';

UPDATE category SET parent_id = 1, sort_order = id - 1 WHERE id IN (2, 3, 4) AND parent_id IS NULL;
UPDATE category SET parent_id = 6, sort_order = id - 6 WHERE id IN (7, 8) AND parent_id IS NULL;
UPDATE category SET parent_id = 9, sort_order = 1 WHERE id = 10 AND parent_id IS NULL;

INSERT IGNORE INTO category(name, code, parent_id, sort_order)
SELECT '数码评测', 'review', id, 2 FROM category WHERE code = 'tech';
INSERT IGNORE INTO category(name, code, parent_id, sort_order)
SELECT '人工智能', 'ai', id, 3 FROM category WHERE code = 'tech';
INSERT IGNORE INTO category(name, code, parent_id, sort_order) VALUES
  ('生活', 'life', NULL, 5), ('美食', 'food', NULL, 6), ('知识', 'knowledge', NULL, 7),
  ('运动', 'sports', NULL, 8), ('时尚', 'fashion', NULL, 9), ('娱乐', 'entertainment', NULL, 10),
  ('影视', 'film', NULL, 11), ('舞蹈', 'dance', NULL, 12);

CREATE TABLE IF NOT EXISTS video_like (
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_favorite (
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, video_id),
  INDEX idx_favorite_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_follow (
  follower_id BIGINT NOT NULL,
  following_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, following_id),
  INDEX idx_follow_following_created (following_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  video_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  content VARCHAR(1000) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_comment_video_created (video_id, created_at),
  INDEX idx_comment_parent_created (parent_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_view_history (
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  watched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, video_id),
  INDEX idx_history_user_watched (user_id, watched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_play_progress (
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  progress_seconds INT NOT NULL DEFAULT 0,
  duration_seconds INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_danmaku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  video_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(100) NOT NULL,
  mode VARCHAR(16) NOT NULL DEFAULT 'SCROLL',
  color VARCHAR(7) NOT NULL DEFAULT '#FFFFFF',
  time_ms INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_danmaku_video_time (video_id, time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  recipient_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  type VARCHAR(16) NOT NULL,
  content VARCHAR(500) NOT NULL,
  video_id BIGINT NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_notification_recipient_created (recipient_id, created_at),
  INDEX idx_notification_recipient_type (recipient_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS private_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  from_id BIGINT NOT NULL,
  to_id BIGINT NOT NULL,
  content VARCHAR(500) NOT NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_message_to_read (to_id, read_at),
  INDEX idx_message_thread (from_id, to_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transcode_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  video_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 3,
  error_message VARCHAR(1000),
  hls_playlist_key VARCHAR(500),
  renditions VARCHAR(200),
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_transcode_status_created (status, created_at),
  INDEX idx_transcode_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  video_id BIGINT NOT NULL,
  auditor_id BIGINT NULL,
  result VARCHAR(16) NOT NULL,
  reason VARCHAR(500) NULL,
  ai_score INT NULL,
  ai_detail TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_video_audit_video (video_id),
  INDEX idx_video_audit_result_created (result, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS system_setting (
  setting_key VARCHAR(64) PRIMARY KEY,
  setting_value VARCHAR(255) NOT NULL,
  description VARCHAR(255) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS live_room (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(80) NOT NULL,
  cover_object_key VARCHAR(500) NULL,
  stream_key VARCHAR(64) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL DEFAULT 'READY',
  viewer_count INT NOT NULL DEFAULT 0,
  danmaku_count INT NOT NULL DEFAULT 0,
  started_at DATETIME NULL,
  ended_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_live_status_created (status, created_at),
  INDEX idx_live_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS live_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  type VARCHAR(16) NOT NULL DEFAULT 'CHAT',
  content VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_live_message_room_created (room_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO system_setting(setting_key, setting_value, description) VALUES
  ('allow_registration', 'true', '允许新用户注册'),
  ('danmaku_enabled', 'true', '弹幕功能'),
  ('comment_enabled', 'true', '评论功能'),
  ('video_review_enabled', 'true', '视频投稿需要审核'),
  ('like_enabled', 'true', '点赞功能'),
  ('coin_enabled', 'true', '投币功能'),
  ('favorite_enabled', 'true', '收藏功能'),
  ('follow_enabled', 'true', '关注功能');

-- 弹幕防挡脸：FFmpeg 抽帧分析任务的执行状态（每个视频一条）
CREATE TABLE IF NOT EXISTS video_danmaku_analysis (
  video_id BIGINT PRIMARY KEY,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  frame_count INT NOT NULL DEFAULT 0,
  message VARCHAR(500) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 弹幕防挡脸：按时间段落库的人脸区域，比例均为画面高度的 0~1 归一化值
CREATE TABLE IF NOT EXISTS video_danmaku_mask (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  video_id BIGINT NOT NULL,
  start_ms INT NOT NULL,
  end_ms INT NOT NULL,
  mask_top DECIMAL(5,4) NOT NULL,
  mask_bottom DECIMAL(5,4) NOT NULL,
  face_ratio DECIMAL(5,4) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_danmaku_mask_video_time (video_id, start_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 弹幕关键词过滤词库，action=BLOCK 直接拒绝，REVIEW 放行但记录人工复核日志
CREATE TABLE IF NOT EXISTS sensitive_word (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  word VARCHAR(64) NOT NULL,
  action VARCHAR(16) NOT NULL DEFAULT 'BLOCK',
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sensitive_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO sensitive_word(word, action) VALUES
  ('加微信', 'BLOCK'),
  ('加V', 'BLOCK'),
  ('代开发票', 'BLOCK'),
  ('裸聊', 'BLOCK'),
  ('博彩', 'BLOCK'),
  ('刷单', 'BLOCK'),
  ('办证', 'BLOCK'),
  ('毒品', 'BLOCK'),
  ('枪支', 'BLOCK'),
  ('赌博', 'BLOCK'),
  ('上线啦', 'REVIEW'),
  ('私聊', 'REVIEW'),
  ('二维码', 'REVIEW'),
  ('站外链接', 'REVIEW'),
  ('低价出', 'REVIEW');
-- 转码进度回传：每个任务一行，阶段 + 百分比 + 实时速度
CREATE TABLE IF NOT EXISTS transcode_task_progress (
  task_id BIGINT PRIMARY KEY,
  video_id BIGINT NOT NULL,
  percent INT NOT NULL DEFAULT 0,
  stage VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  fps DECIMAL(10,2) NULL,
  speed DECIMAL(10,2) NULL,
  eta_seconds INT NULL,
  message VARCHAR(500) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_transcode_progress_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 每次转码尝试的明细，用于重试统计与成功率/耗时观测
CREATE TABLE IF NOT EXISTS transcode_task_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(1000) NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  duration_ms BIGINT NULL,
  INDEX idx_transcode_attempt_task (task_id),
  INDEX idx_transcode_attempt_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
