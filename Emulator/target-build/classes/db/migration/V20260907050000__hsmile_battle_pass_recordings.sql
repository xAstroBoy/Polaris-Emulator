-- HSmile feature batch B (2026-09-07): battle pass (season / tiers / quests / progress) and room recordings.

CREATE TABLE IF NOT EXISTS `battle_pass_seasons` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL,
    `starts_at` INT NOT NULL DEFAULT 0,
    `ends_at` INT NOT NULL DEFAULT 0,
    `premium_price` INT NOT NULL DEFAULT 0,
    `points_type` INT NOT NULL DEFAULT 5,
    `xp_per_tier` INT NOT NULL DEFAULT 500,
    `tier_count` INT NOT NULL DEFAULT 30,
    `active` TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `battle_pass_tiers` (
    `season_id` INT NOT NULL,
    `tier` INT NOT NULL,
    `free_type` VARCHAR(16) NOT NULL DEFAULT '',
    `free_data` VARCHAR(64) NOT NULL DEFAULT '',
    `free_amount` INT NOT NULL DEFAULT 0,
    `premium_type` VARCHAR(16) NOT NULL DEFAULT '',
    `premium_data` VARCHAR(64) NOT NULL DEFAULT '',
    `premium_amount` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`season_id`, `tier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `battle_pass_quests` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `season_id` INT NOT NULL,
    `period` ENUM('daily','weekly','season') NOT NULL DEFAULT 'daily',
    `metric` VARCHAR(32) NOT NULL,
    `target` INT NOT NULL DEFAULT 1,
    `xp` INT NOT NULL DEFAULT 0,
    `title` VARCHAR(96) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    KEY `season` (`season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_battle_pass` (
    `user_id` INT NOT NULL,
    `season_id` INT NOT NULL,
    `xp` INT NOT NULL DEFAULT 0,
    `premium` TINYINT(1) NOT NULL DEFAULT 0,
    `claimed_free` TEXT NULL,
    `claimed_premium` TEXT NULL,
    PRIMARY KEY (`user_id`, `season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_battle_pass_quests` (
    `user_id` INT NOT NULL,
    `quest_id` INT NOT NULL,
    `period_key` VARCHAR(16) NOT NULL,
    `progress` INT NOT NULL DEFAULT 0,
    `completed_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `quest_id`, `period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `room_recordings` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` INT NOT NULL,
    `room_id` INT NOT NULL DEFAULT 0,
    `room_name` VARCHAR(96) NOT NULL DEFAULT '',
    `seconds` INT NOT NULL DEFAULT 0,
    `bytes` BIGINT NOT NULL DEFAULT 0,
    `extension` VARCHAR(8) NOT NULL DEFAULT 'webm',
    `created_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `owner` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- First season: 60 days from the deploy, 30 tiers of 500 XP, premium track for 50 diamonds.
INSERT INTO `battle_pass_seasons` (`name`, `starts_at`, `ends_at`, `premium_price`, `points_type`, `xp_per_tier`, `tier_count`, `active`)
SELECT 'Stagione 1', UNIX_TIMESTAMP(), UNIX_TIMESTAMP() + 60 * 86400, 50, 5, 500, 30, 1
WHERE NOT EXISTS (SELECT 1 FROM `battle_pass_seasons`);

SET @bp_season := (SELECT MIN(`id`) FROM `battle_pass_seasons` WHERE `active` = 1);

INSERT IGNORE INTO `battle_pass_tiers` (`season_id`, `tier`, `free_type`, `free_data`, `free_amount`, `premium_type`, `premium_data`, `premium_amount`)
SELECT @bp_season, t.n,
       CASE WHEN t.n % 10 = 0 THEN 'diamonds' WHEN t.n % 3 = 0 THEN 'duckets' ELSE 'credits' END, '',
       CASE WHEN t.n % 10 = 0 THEN 5 + t.n / 10 * 5 WHEN t.n % 3 = 0 THEN 500 + t.n * 50 ELSE 250 + t.n * 25 END,
       CASE WHEN t.n % 5 = 0 THEN 'diamonds' WHEN t.n % 2 = 0 THEN 'duckets' ELSE 'credits' END, '',
       CASE WHEN t.n % 5 = 0 THEN 10 + t.n / 5 * 5 WHEN t.n % 2 = 0 THEN 1000 + t.n * 100 ELSE 500 + t.n * 50 END
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
      UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20
      UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION SELECT 30) t
WHERE @bp_season IS NOT NULL;

INSERT INTO `battle_pass_quests` (`season_id`, `period`, `metric`, `target`, `xp`, `title`)
SELECT @bp_season, q.period, q.metric, q.target, q.xp, q.title FROM (
    SELECT 'daily' period, 'chat' metric, 50 target, 100 xp, 'Scrivi 50 messaggi in chat' title
    UNION ALL SELECT 'daily', 'rooms', 5, 100, 'Visita 5 stanze'
    UNION ALL SELECT 'daily', 'online', 30, 150, 'Resta in una stanza per 30 minuti'
    UNION ALL SELECT 'daily', 'respect', 3, 50, 'Dai 3 rispetti'
    UNION ALL SELECT 'weekly', 'chat', 500, 400, 'Scrivi 500 messaggi in chat'
    UNION ALL SELECT 'weekly', 'rooms', 30, 300, 'Visita 30 stanze'
    UNION ALL SELECT 'weekly', 'online', 300, 500, 'Resta nelle stanze per 5 ore'
    UNION ALL SELECT 'weekly', 'furni', 50, 300, 'Posiziona 50 furni'
    UNION ALL SELECT 'weekly', 'friends', 3, 200, 'Fai 3 nuovi amici'
    UNION ALL SELECT 'season', 'login', 30, 1500, 'Entra in hotel per 30 giorni'
) q
WHERE @bp_season IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `battle_pass_quests` WHERE `season_id` = @bp_season);
