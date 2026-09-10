-- Upstream registra gli handler di wf_cnd_user_cooldown e wf_cnd_first_trg ma non spedisce nessun
-- furni: quei nomi non esistono ne' su Habbo ne' altrove, li ha scelti lui aspettandosi che
-- l'hotel metta la scatola. I bundle sono stati generati riscrivendo quello di wf_cnd_daily_trg,
-- che e' la stessa famiglia di condizioni e ha gia' la grafica giusta.
--
-- Restano pubblici come le altre 71 condizioni della pagina: limitano quando il wired scatta,
-- non regalano niente, e i box che distribuiscono valore sono dietro acc_superwired.
INSERT INTO `items_base`
    (`id`, `sprite_id`, `public_name`, `item_name`, `type`, `width`, `length`, `stack_height`,
     `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`, `allow_gift`, `allow_trade`,
     `allow_recycle`, `allow_marketplace_sell`, `allow_inventory_stack`, `interaction_type`,
     `interaction_modes_count`, `vending_ids`, `asset_states`)
VALUES
    (884062940, 884062940, 'CONDIZIONE: Attesa fra un innesco e l''altro', 'wf_cnd_user_cooldown',
     's', 1, 1, 0.65, '1', '0', '0', '1', '1', '1', '0', '0', '1', 'wf_cnd_user_cooldown', 102, '0', 2),
    (884062941, 884062941, 'CONDIZIONE: Solo il primo innesco di sempre', 'wf_cnd_first_trg',
     's', 1, 1, 0.65, '1', '0', '0', '1', '1', '1', '0', '0', '1', 'wf_cnd_first_trg', 102, '0', 2)
ON DUPLICATE KEY UPDATE
    `public_name` = VALUES(`public_name`),
    `interaction_type` = VALUES(`interaction_type`);

-- In vendita accanto al box giornaliero, stessa pagina e stesso prezzo.
INSERT INTO `catalog_items`
    (`page_id`, `item_ids`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`, `amount`,
     `song_id`, `order_number`)
SELECT ci.`page_id`, '884062940', 'wf_cnd_user_cooldown', ci.`cost_credits`, ci.`cost_points`,
       ci.`points_type`, 1, 0, ci.`order_number`
FROM `catalog_items` ci
WHERE ci.`item_ids` = '884062939'
LIMIT 1;

INSERT INTO `catalog_items`
    (`page_id`, `item_ids`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`, `amount`,
     `song_id`, `order_number`)
SELECT ci.`page_id`, '884062941', 'wf_cnd_first_trg', ci.`cost_credits`, ci.`cost_points`,
       ci.`points_type`, 1, 0, ci.`order_number`
FROM `catalog_items` ci
WHERE ci.`item_ids` = '884062939'
LIMIT 1;
