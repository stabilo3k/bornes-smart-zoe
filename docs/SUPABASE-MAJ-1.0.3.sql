-- Version 1.0.3 : puissance observée (kw) et états de borne (table etats).
-- À exécuter UNE fois dans Supabase > SQL Editor, avant de distribuer la 1.0.3. Peut être relancé sans danger.

-- 1) Puissance maximale observée (kW), facultative.
alter table public.releves add column if not exists kw numeric(5,1) check (kw is null or (kw >= 0.5 and kw <= 400));
grant select (kw) on public.releves to anon;

-- 2) États de borne : panne, occupée, file, libre (sur place) ; fonctionne / ne fonctionne pas / panne déclarés par un tiers (non testés).
create table if not exists public.etats (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique check (char_length(client_id) between 3 and 60),
  device text not null check (char_length(device) between 8 and 64),
  station_id text not null check (char_length(station_id) between 1 and 80),
  station_name text not null default '' check (char_length(station_name) <= 120),
  lat double precision not null check (lat between 49.4 and 51.6),
  lon double precision not null check (lon between 2.5 and 6.5),
  kind text not null check (kind in ('panne','occupee','file','libre','marche','nemarchepas')),
  level text not null default 'test' check (level in ('test','declare')),
  car text not null default '' check (char_length(car) <= 40),
  source text not null default '' check (char_length(source) <= 40),
  created_at timestamptz not null default now()
);

alter table public.etats enable row level security;
drop policy if exists "lecture" on public.etats;
create policy "lecture" on public.etats for select to anon using (true);
drop policy if exists "ajout" on public.etats;
create policy "ajout" on public.etats for insert to anon with check (true);
revoke all on public.etats from anon;
-- L'identifiant de téléphone (device) reste illisible : lecture limitée aux colonnes publiques.
grant select (id, client_id, station_id, station_name, lat, lon, kind, level, car, source, created_at) on public.etats to anon;
grant insert on public.etats to anon;

create index if not exists etats_created_idx on public.etats (created_at desc);

-- Limite anti-abus : 60 signalements par téléphone et par 24 h.
create or replace function public.limite_etats() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.etats where device = new.device and created_at > now() - interval '24 hours') >= 60 then
    raise exception 'limite quotidienne atteinte';
  end if;
  return new;
end $$;
drop trigger if exists limite_etats on public.etats;
create trigger limite_etats before insert on public.etats for each row execute function public.limite_etats();
