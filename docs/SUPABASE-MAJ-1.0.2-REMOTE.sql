-- Version 1.0.2 : relevés « à distance » (borne choisie sur la carte ou à plus de 60 m du GPS).
-- À exécuter UNE fois dans Supabase > SQL Editor, avant de distribuer la 1.0.2.
alter table public.releves add column if not exists remote boolean not null default false;
-- La lecture publique doit pouvoir lire la colonne (même règle que les autres colonnes publiques).
grant select (remote) on public.releves to anon;
