-- Purge des données du serveur : applique les durées annoncées dans la FAQ « données » (1.3.1).
--   états de borne : 90 jours ; identifiant de téléphone (device) : effacé après 12 mois ; relevés, fiches et pastilles : supprimés après 3 ans.
-- À exécuter dans l'éditeur SQL de Supabase (par le mainteneur), une fois par mois, ou à planifier avec pg_cron (voir la fin).
-- La colonne device est « not null » : l'identifiant est remplacé par une valeur aléatoire sans lien avec le téléphone (préfixe « efface- »).
-- Rien n'est exécuté automatiquement par l'application. Sauvegardez avant la première exécution (voir docs/ et le workflow supabase-export).

delete from public.etats where created_at < now() - interval '90 days';

update public.releves   set device = 'efface-' || left(md5(random()::text), 8) where device not like 'efface-%' and created_at < now() - interval '12 months';
update public.pastilles set device = 'efface-' || left(md5(random()::text), 8) where device not like 'efface-%' and created_at < now() - interval '12 months';
update public.fiches    set device = 'efface-' || left(md5(random()::text), 8) where device not like 'efface-%' and created_at < now() - interval '12 months';

delete from public.releves   where created_at < now() - interval '3 years';
delete from public.pastilles where created_at < now() - interval '3 years';
delete from public.fiches    where created_at < now() - interval '3 years';

-- Planification facultative (extension pg_cron à activer dans Database > Extensions) : chaque 1er du mois à 03:00 UTC.
-- select cron.schedule('purge-donnees', '0 3 1 * *', $$ <coller les instructions ci-dessus> $$);
