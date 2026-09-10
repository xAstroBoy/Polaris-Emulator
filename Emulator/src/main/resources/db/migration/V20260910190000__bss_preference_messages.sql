-- Gli otto comandi di preferenza BSS rispondevano tutti "Funzione attivata." / "Funzione disattivata.":
-- lo stesso testo per otto cose diverse non dice cosa hai appena acceso ne' su chi ha effetto.
--
-- Ogni riga qui sotto e' scritta leggendo dove il flag viene controllato nel codice, non dal nome del
-- comando: DO_NOT_DISTURB, BLOCK_GIFTS, BLOCK_WHISPERS, BLOCK_MIMIC e BLOCK_KISSES sono verificati sul
-- DESTINATARIO (sono difese tue verso gli altri), mentre GROUP_CHAT_ENABLED, USER_CLICK_ENABLED e
-- RANDOM_WALK_PRIORITY valgono per te. Attenzione alla polarita': per i BLOCK_* "enabled" vuol dire che
-- il blocco e' attivo, per gli altri tre che la funzione e' attiva.
UPDATE `emulator_texts` SET `value` = 'Non disturbare attivo: non ricevi piu'' messaggi in console.'
WHERE `key` = 'commands.success.cmd_bss_dnd.enabled';
UPDATE `emulator_texts` SET `value` = 'Non disturbare spento: puoi ricevere di nuovo messaggi in console.'
WHERE `key` = 'commands.success.cmd_bss_dnd.disabled';

UPDATE `emulator_texts` SET `value` = 'Regali bloccati: nessuno puo'' mandartene.'
WHERE `key` = 'commands.success.cmd_bss_block_gifts.enabled';
UPDATE `emulator_texts` SET `value` = 'Regali sbloccati: puoi riceverne di nuovo.'
WHERE `key` = 'commands.success.cmd_bss_block_gifts.disabled';

UPDATE `emulator_texts` SET `value` = 'Sussurri bloccati: in stanza nessuno puo'' sussurrarti.'
WHERE `key` = 'commands.success.cmd_bss_block_whispers.enabled';
UPDATE `emulator_texts` SET `value` = 'Sussurri sbloccati: puoi riceverne di nuovo.'
WHERE `key` = 'commands.success.cmd_bss_block_whispers.disabled';

UPDATE `emulator_texts` SET `value` = 'Imitazione bloccata: nessuno puo'' copiarti il look.'
WHERE `key` = 'commands.success.cmd_bss_block_mimic.enabled';
UPDATE `emulator_texts` SET `value` = 'Imitazione sbloccata: possono di nuovo copiarti il look.'
WHERE `key` = 'commands.success.cmd_bss_block_mimic.disabled';

UPDATE `emulator_texts` SET `value` = 'Baci bloccati: nessuno puo'' baciarti.'
WHERE `key` = 'commands.success.cmd_bss_block_kisses.enabled';
UPDATE `emulator_texts` SET `value` = 'Baci sbloccati: possono di nuovo baciarti.'
WHERE `key` = 'commands.success.cmd_bss_block_kisses.disabled';

UPDATE `emulator_texts` SET `value` = 'Chat di gruppo attiva: ricevi e mandi i messaggi dei gruppi.'
WHERE `key` = 'commands.success.cmd_bss_group_chat.enabled';
UPDATE `emulator_texts` SET `value` = 'Chat di gruppo silenziata: non ricevi ne'' mandi messaggi dei gruppi.'
WHERE `key` = 'commands.success.cmd_bss_group_chat.disabled';

-- Questo e' il flag di :tc. Da adesso non spegne solo il menu: gli avatar smettono proprio di
-- intercettare il puntatore, cosi' il clic arriva al pavimento dietro di loro.
UPDATE `emulator_texts` SET `value` = 'Gli avatar sono di nuovo cliccabili.'
WHERE `key` = 'commands.success.cmd_bss_user_click.enabled';
UPDATE `emulator_texts` SET `value` = 'Avatar trasparenti al clic: i tuoi clic passano attraverso le persone e arrivano al pavimento.'
WHERE `key` = 'commands.success.cmd_bss_user_click.disabled';

UPDATE `emulator_texts` SET `value` = 'Camminata varia attiva: a parita'' di percorso non prendi sempre la stessa strada.'
WHERE `key` = 'commands.success.cmd_bss_random_walk.enabled';
UPDATE `emulator_texts` SET `value` = 'Camminata varia spenta: torni a prendere sempre lo stesso percorso.'
WHERE `key` = 'commands.success.cmd_bss_random_walk.disabled';
