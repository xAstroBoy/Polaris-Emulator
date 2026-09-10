-- TrashCommand (:tornado, :sharknado) and FunRoomCommand share cmd_trash, so
-- every alias listed under `commands.description.cmd_trash` moves back to staff
-- only. DISALLOWED (0) also removes them from getCommandsForRank, so they no
-- longer appear in :comandi for Member/VIP/X - the commands stay hidden.
UPDATE `permission_definitions`
SET `rank_1` = 0,
    `rank_2` = 0,
    `rank_3` = 0,
    `rank_4` = 1,
    `rank_5` = 1,
    `rank_6` = 1,
    `rank_7` = 1,
    `comment` = 'Eventi stanza e catastrofi: solo staff (rank 4+), nascosti agli altri ranghi.'
WHERE `permission_key` = 'cmd_trash';

UPDATE `permissions`
SET `cmd_trash` = CASE WHEN `level` >= 4 THEN '1' ELSE '0' END;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_trash', ':tornado [utente] / :sharknado [utente] - colpisce te o il giocatore scelto (solo staff).'),
    ('commands.error.cmd_fun_room.permission', 'Solo lo staff può usare questo comando.'),
    ('commands.help.cmd_fun_room', 'EVENTI STANZA (solo staff): :disco, :rave, :terremoto, :cannoni, :levitazione, :girotondo, :invasionefufo, :piratiparty, :ghostparty, :robotparty, :pioggiadisco, :caos, :statue, :carnevale.\r\nATMOSFERE: :spazio, :aurora, :temporale, :tramonto, :neonvoid, :blackout, :oceano, :inferno.\r\nSU UN UTENTE: :rapisci, :rimbalza, :vola, :orbita, :frullatore, :yoyo, :fantasma, :papera, :mummia, :zombie, :goblin, :alieno <utente>.\r\nINTERAZIONI: :scambio, :calamita, :duello, :abbraccio, :telepatia, :catapulta <utente>.\r\nCATASTROFI: :tornado [utente], :sharknado [utente].')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
