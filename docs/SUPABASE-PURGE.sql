-- Conservation des données (annoncée dans la FAQ de l'application) :
--   états de borne : 90 jours (l'application ne lit que les 14 derniers jours)
--   identifiant aléatoire du téléphone (colonne device) : effacé après 12 mois
--   relevés et fiches : supprimés après 3 ans
-- NE PAS EXÉCUTER sans l'accord explicite du responsable du projet. Les relevés ne seraient supprimés qu'après 3 ans ; les états
-- (90 jours) et l'effacement de l'identifiant (12 mois) agissent plus tôt.
-- Étape 1 (lecture seule) : ce qui serait touché.
select 'etats > 90 j' as t, count(*) from public.etats where created_at < now() - interval '90 days'
union all select 'releves device à effacer', count(*) from public.releves where created_at < now() - interval '12 months' and device <> 'efface-12m'
union all select 'fiches device à effacer', count(*) from public.fiches where created_at < now() - interval '12 months' and device <> 'efface-12m'
union all select 'releves > 3 ans', count(*) from public.releves where created_at < now() - interval '3 years'
union all select 'fiches > 3 ans', count(*) from public.fiches where created_at < now() - interval '3 years';

-- Étape 2 : à lancer à la main ou à planifier (pg_cron, mensuel). La valeur 'efface-12m' respecte la contrainte de longueur (8 à 64 caractères).
-- delete from public.etats where created_at < now() - interval '90 days';
-- update public.releves set device = 'efface-12m' where created_at < now() - interval '12 months' and device <> 'efface-12m';
-- update public.etats   set device = 'efface-12m' where created_at < now() - interval '12 months' and device <> 'efface-12m';
-- update public.fiches  set device = 'efface-12m' where created_at < now() - interval '12 months' and device <> 'efface-12m';
-- delete from public.releves where created_at < now() - interval '3 years';
-- delete from public.fiches  where created_at < now() - interval '3 years';
-- Attention : la règle anti-abus (limite par téléphone) lit device ; vérifier le déclencheur limite_releves avant d'exécuter l'étape 2.
