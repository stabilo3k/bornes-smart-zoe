-- Version 1.2 : motif du refus (releves.motif) et pastille vue (fiches.pastille).
-- À exécuter UNE fois dans Supabase > SQL Editor. Peut être relancé sans danger. L'application 1.2 détecte seule la présence de ces colonnes.
alter table public.releves add column if not exists motif text check (motif is null or motif in ('voyant','eteinte','badge'));
grant select (motif) on public.releves to anon;
alter table public.fiches add column if not exists pastille text check (pastille is null or pastille in ('verte','blanche','aucune'));
grant select (pastille) on public.fiches to anon;
