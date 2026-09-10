-- Un testo senza il segnaposto che il codice sostituisce perde il valore in silenzio: ":summon
-- dorian" su un utente offline rispondeva "%% non e' in linea!" perche' il testo scriveva "%%" mentre
-- SummonCommand sostituisce "%user%". Nessun errore, nessun log: il nome semplicemente non c'era.
--
-- Sono stati confrontati tutti i 186 testi che il codice legge insieme a una .replace("%...%"): questi
-- sono quelli in cui il valore andava perso. Gli altri combaciano gia'.
--
-- Restano fuori di proposito commands.generic.cmd_summon.self e cmd_stalk.self ("Ho provato qualcosa
-- di stupido"): la battuta funziona senza nome, e il nome saresti comunque tu.
UPDATE `emulator_texts` SET `value` = '%user% non è in linea!'
WHERE `key` = 'commands.error.cmd_summon.not_found';

UPDATE `emulator_texts` SET `value` = 'L''Habbo %username% non esiste.'
WHERE `key` = 'commands.error.cmd_allow_trading.user_not_found';

-- GiveRankCommand sostituisce sia %username% sia %id% (che e' il NOME del rango, non un numero):
-- senza il secondo non si capisce quale rango e' stato rifiutato.
UPDATE `emulator_texts` SET `value` = 'Non puoi dare a %username% il rango %id%: è più alto del tuo!'
WHERE `key` = 'commands.error.cmd_give_rank.higher';

UPDATE `emulator_texts` SET `value` = '%username% ha un rango più alto del tuo, non puoi dargli %id%!'
WHERE `key` = 'commands.error.cmd_give_rank.higher.other';

UPDATE `emulator_texts` SET `value` = '%username% non è online, non posso dargli il rango %id%!'
WHERE `key` = 'commands.error.cmd_give_rank.user_offline';

UPDATE `emulator_texts` SET `value` = 'Inventario dei bot di %username% svuotato!'
WHERE `key` = 'commands.succes.cmd_empty_bots.cleared';

UPDATE `emulator_texts` SET `value` = 'Inventario degli animali di %username% svuotato!'
WHERE `key` = 'commands.succes.cmd_empty_pets.cleared';

UPDATE `emulator_texts` SET `value` = 'Cronologia chat di gruppo rimossa dalla tua vista (%count% messaggi).'
WHERE `key` = 'commands.success.cmd_bss_clear_group_chat';

-- Il comando serve proprio a leggere quanto vale un raro, e mostrava solo il nome.
UPDATE `emulator_texts` SET `value` = '%name%: %credits% crediti, %points% %type%.'
WHERE `key` = 'commands.generic.cmd_bss_rare_value';

UPDATE `emulator_texts` SET `value` = 'Il server si spegne fra %minutes% minuti. Da adesso le modifiche potrebbero non essere salvate!'
WHERE `key` = 'generic.shutdown';

-- L'avviso allo staff diceva solo in quale stanza, non di chi era ne' per quanto era stato bloccato.
UPDATE `emulator_texts` SET `value` = 'Stanza: %roomname% (di %owner%) - wired bloccato per %minutes% minuti.'
WHERE `key` = 'wired.abuse.staff.message';

UPDATE `emulator_texts` SET `value` = 'Non puoi comprare %itemname%: hai già raggiunto il limite di %limit% pezzi in edizione limitata per oggi. Riprova domani.'
WHERE `key` = 'error.catalog.buy.limited.daily.total';
