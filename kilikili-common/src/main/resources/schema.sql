CREATE TABLE IF NOT EXISTS account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  avatar VARCHAR(500),
  gender VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
  birthday DATE,
  bio VARCHAR(200),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE account ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE account ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE account ADD COLUMN IF NOT EXISTS gender VARCHAR(16) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE account ADD COLUMN IF NOT EXISTS birthday DATE NULL;
ALTER TABLE account ADD COLUMN IF NOT EXISTS bio VARCHAR(200) NULL;

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

ALTER TABLE video ADD COLUMN IF NOT EXISTS category_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE video ADD COLUMN IF NOT EXISTS cover_object_key VARCHAR(500) NULL;
ALTER TABLE video ADD COLUMN IF NOT EXISTS duration_seconds INT NOT NULL DEFAULT 0;
ALTER TABLE video ADD COLUMN IF NOT EXISTS creation_declaration VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL';
ALTER TABLE video ADD INDEX IF NOT EXISTS idx_video_recommend (status, category_id, created_at);

CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO category(id, name, sort_order) VALUES
  (1, '动画', 10), (2, 'MAD/AMV', 20), (3, 'MMD/3D', 30), (4, '配音', 40),
  (5, '音乐', 50), (6, '游戏', 60), (7, '游戏直播', 70), (8, '电子竞技', 80),
  (9, '科技', 90), (10, '编程', 100);

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

ALTER TABLE video_comment ADD COLUMN IF NOT EXISTS parent_id BIGINT NULL;
ALTER TABLE video_comment ADD INDEX IF NOT EXISTS idx_comment_parent_created (parent_id, created_at);

CREATE TABLE IF NOT EXISTS video_view_history (
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  watched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, video_id),
  INDEX idx_history_user_watched (user_id, watched_at)
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
