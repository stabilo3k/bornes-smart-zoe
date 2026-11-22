-- BorneNeutre 1.3.2 : codes « bleue » et « autre » (pastilles, fiches), état « carte ou appli refusée », motif « carte », démarrage de la charge, vue par téléphones distincts.
-- Revu le 21/11/2026 sur les contraintes RÉELLES du serveur (requête de lecture seule sur pg_constraint) : noms et valeurs ci-dessous vérifiés.
-- EXÉCUTÉ le 21/11/2026 à 20 h 05 (accord du mainteneur ; contraintes additives, aucune donnée modifiée). Peut être relancé sans danger : à coller dans Supabase > SQL Editor > Run.
-- À exécuter AVANT d'installer une app qui envoie ces valeurs : une valeur inconnue du serveur ferait refuser la ligne (code 23514, ligne perdue côté app).
-- Aucune donnée existante n'est modifiée ; les contraintes ne font qu'accepter des valeurs de plus.
-- L'avertissement « destructif » de Supabase concerne les « drop constraint if exists » suivis de leur recréation : confirmer.

-- 1) pastilles.code : + bleue, autre   (avant : verte, blanche, aucune)
alter table public.pastilles drop constraint if exists pastilles_code_check;
alter table public.pastilles add constraint pastilles_code_check check (code in ('verte','blanche','aucune','bleue','autre'));

-- 2) fiches.pastille : mêmes valeurs   (avant : null ou verte, blanche, aucune)
alter table public.fiches drop constraint if exists fiches_pastille_check;
alter table public.fiches add constraint fiches_pastille_check check (pastille is null or pastille in ('verte','blanche','aucune','bleue','autre'));

-- 3) etats.kind : + carterefusee   (avant : panne, occupee, file, libre, marche, nemarchepas)
alter table public.etats drop constraint if exists etats_kind_check;
alter table public.etats add constraint etats_kind_check check (kind in ('panne','occupee','file','libre','marche','nemarchepas','carterefusee'));

-- 4) releves.motif : + carte   (avant : null ou voyant, eteinte, badge)
alter table public.releves drop constraint if exists releves_motif_check;
alter table public.releves add constraint releves_motif_check check (motif is null or motif in ('voyant','eteinte','badge','carte'));

-- 5) releves.demarrage : colonne facultative « démarrage de la charge » (carte/badge, appli, QR code, carte bancaire, autre, je ne sais pas).
--    Non lisible publiquement (aucun « grant select » pour anon) : l'insertion fonctionne par le droit d'insertion de la table.
alter table public.releves add column if not exists demarrage text;
alter table public.releves drop constraint if exists releves_demarrage_check;
alter table public.releves add constraint releves_demarrage_check check (demarrage is null or demarrage in ('carte','appli','qr','cb','autre','nsp'));

-- 6) Vue par téléphones DISTINCTS pour « vue 1 fois / confirmée par 2 téléphones » (aucun identifiant exposé : seulement des comptes).
create or replace view public.pastilles_par_borne as
  select station_id, code, count(*) as n_vues, count(distinct device) as n_telephones, max(created_at) as derniere
  from public.pastilles where created_at > now() - interval '12 months' group by station_id, code;
alter view public.pastilles_par_borne set (security_invoker = false);
revoke all on public.pastilles_par_borne from anon;
grant select on public.pastilles_par_borne to anon;

-- 7) Contrôle après exécution (lecture seule) : doit renvoyer les 5 valeurs ci-dessus pour chaque contrainte.
-- select conrelid::regclass::text tbl, conname, pg_get_constraintdef(oid) from pg_constraint
--  where conname in ('pastilles_code_check','fiches_pastille_check','etats_kind_check','releves_motif_check','releves_demarrage_check') order by 1,2;
