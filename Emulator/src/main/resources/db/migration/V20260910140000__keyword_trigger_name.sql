-- L'innesco si chiamava "Un Utente dice qualcosa", ma non reagisce a qualsiasi cosa: reagisce alla
-- parola chiave che gli configuri dentro. Il nome nascondeva l'unica impostazione che il box ha, e
-- adesso che di default nasconde il messaggio che lo fa scattare conta ancora di piu' che si capisca
-- dal catalogo cosa fa.
UPDATE `items_base`
SET `public_name` = 'INNESCO: Un Utente dice una parola chiave'
WHERE `item_name` = 'wf_trg_says_something';
