CREATE TABLE IF NOT EXISTS system_setting (
  setting_key VARCHAR(64) PRIMARY KEY,
  setting_value VARCHAR(255) NOT NULL,
  description VARCHAR(255) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
