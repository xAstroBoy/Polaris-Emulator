-- :diagonali sembrava rotto perche' era muto a meta': esiste il testo di quando le spegni, non
-- quello di quando le accendi. Il comando funzionava, ma accendendole non arrivava nessun fumetto,
-- quindi sembrava che non facesse niente.
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.succes.cmd_diagonal.enabled', 'Ora puoi camminare in diagonale!'),
    ('commands.succes.cmd_diagonal.disabled', 'Non puoi piu'' camminare in diagonale!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- Camminata diagonale accesa ovunque: la colonna nasce gia' a '1', quindi restano indietro solo le
-- stanze create prima. Il proprietario puo' sempre spegnerla con :diagonali.
UPDATE `rooms` SET `move_diagonally` = '1' WHERE `move_diagonally` = '0';
