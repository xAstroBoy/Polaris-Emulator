-- HSmile feature batch A (2026-09-07): privacy preferences, login rewards, video curtain, per-user UI theme.

ALTER TABLE `user_look_extras`
    ADD COLUMN IF NOT EXISTS `hide_ornaments` TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `mention_privacy` TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `call_privacy` TINYINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS `login_rewards` (
    `day` TINYINT NOT NULL,
    `reward_type` VARCHAR(16) NOT NULL DEFAULT 'credits',
    `reward_data` VARCHAR(64) NOT NULL DEFAULT '',
    `amount` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `login_rewards` (`day`, `reward_type`, `reward_data`, `amount`) VALUES
    (1, 'credits', '', 500),
    (2, 'duckets', '', 1000),
    (3, 'diamonds', '', 5),
    (4, 'credits', '', 1000),
    (5, 'duckets', '', 2500),
    (6, 'diamonds', '', 10),
    (7, 'diamonds', '', 25);

CREATE TABLE IF NOT EXISTS `user_login_rewards` (
    `user_id` INT NOT NULL,
    `current_day` TINYINT NOT NULL DEFAULT 0,
    `streak` INT NOT NULL DEFAULT 0,
    `record` INT NOT NULL DEFAULT 0,
    `last_claim_date` DATE NULL DEFAULT NULL,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `room_video_curtain` (
    `room_id` INT NOT NULL,
    `video_id` VARCHAR(16) NOT NULL,
    `playing` TINYINT(1) NOT NULL DEFAULT 1,
    `position` INT NOT NULL DEFAULT 0,
    `updated_at` INT NOT NULL DEFAULT 0,
    `setter_id` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_ui_theme` (
    `user_id` INT NOT NULL,
    `theme_json` MEDIUMTEXT NOT NULL,
    `updated_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
