-- :collisioni disegna le caselle di collisione vere sotto ai furni, solo per chi guarda e senza
-- scrivere niente nella stanza. Serve a chi costruisce, quindi arriva a proprietari e dirittati
-- invece di restare staff: il comando poi verifica proprieta', diritti o staff da solo, perche'
-- "ha i diritti" non e' "e' il proprietario" e non va confuso col permesso ROOM_OWNER.
UPDATE `permission_definitions`
SET `rank_1` = 1,
    `rank_2` = 1,
    `rank_3` = 1,
    `rank_4` = 1,
    `rank_5` = 1,
    `rank_6` = 1,
    `rank_7` = 1,
    `comment` = 'Vedi le collisioni dei furni: proprietario della stanza, chi ha i diritti, o staff.'
WHERE `permission_key` = 'cmd_debugviewcollisions';

-- La tabella `permissions` ha una colonna per comando, e questo non ce l'aveva: va creata prima di
-- scriverci, altrimenti l'avvio si ferma su uno schema a meta'.
SET @has_column := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'permissions' AND COLUMN_NAME = 'cmd_debugviewcollisions');

SET @sql := IF(@has_column = 0,
    'ALTER TABLE `permissions` ADD COLUMN `cmd_debugviewcollisions` ENUM(''0'',''1'') NOT NULL DEFAULT ''1''',
    'DO 0');

PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

UPDATE `permissions` SET `cmd_debugviewcollisions` = '1';

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.error.collisions.permission',
     'Puoi vedere le collisioni solo in una stanza tua o in cui hai i diritti.'),
    ('commands.description.cmd_debugviewcollisions',
     ':collisioni - mostra le caselle occupate dai furni e la direzione in cui sono girati (stanza tua o con diritti).')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
