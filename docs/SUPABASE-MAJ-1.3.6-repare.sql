-- BorneNeutre 1.3.6 : état « repare » (panne réglée ou signalée par erreur) dans la table etats.
-- EXÉCUTÉ le 22/11/2026 vers 10 h 25 (accord du mainteneur à 10 h 19). Additif, aucune donnée modifiée ; peut être relancé sans danger.
-- À exécuter AVANT d'installer une app qui envoie cette valeur (sinon la ligne est refusée par le serveur, code 23514).
-- Valeurs RÉELLES du serveur au 21/11/2026 : panne, occupee, file, libre, marche, nemarchepas, carterefusee (à revérifier par lecture de pg_constraint avant exécution).
alter table public.etats drop constraint if exists etats_kind_check;
alter table public.etats add constraint etats_kind_check check (kind in ('panne','occupee','file','libre','marche','nemarchepas','carterefusee','repare'));
-- Contrôle (lecture seule) : select pg_get_constraintdef(oid) from pg_constraint where conname = 'etats_kind_check';
