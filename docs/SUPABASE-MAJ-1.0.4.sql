-- Version 1.0.4 : fiches de borne (nombre de bornes, prises par borne, puissance déclarée, ajout d'une borne absente).
-- À exécuter UNE fois dans Supabase > SQL Editor, avant de distribuer la 1.0.4. Peut être relancé sans danger.
create table if not exists public.fiches (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique check (char_length(client_id) between 3 and 60),
  device text not null check (char_length(device) between 8 and 64),
  station_id text not null check (char_length(station_id) between 1 and 80),
  station_name text not null default '' check (char_length(station_name) <= 120),
  lat double precision not null check (lat between 49.4 and 51.6),
  lon double precision not null check (lon between 2.5 and 6.5),
  points integer check (points is null or (points between 1 and 30)),
  plugs integer check (plugs is null or (plugs between 1 and 8)),
  kw numeric(5,1) check (kw is null or (kw >= 0.5 and kw <= 400)),
  created_at timestamptz not null default now()
);
alter table public.fiches enable row level security;
drop policy if exists "lecture" on public.fiches;
create policy "lecture" on public.fiches for select to anon using (true);
drop policy if exists "ajout" on public.fiches;
create policy "ajout" on public.fiches for insert to anon with check (true);
revoke all on public.fiches from anon;
-- L'identifiant de téléphone (device) reste illisible.
grant select (id, client_id, station_id, station_name, lat, lon, points, plugs, kw, created_at) on public.fiches to anon;
grant insert on public.fiches to anon;
create index if not exists fiches_station_idx on public.fiches (station_id, created_at desc);
-- Limite anti-abus : 40 fiches par téléphone et par 24 h (fonction « security definer » : elle doit lire la colonne device).
create or replace function public.limite_fiches() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.fiches where device = new.device and created_at > now() - interval '24 hours') >= 40 then
    raise exception 'limite quotidienne atteinte';
  end if;
  return new;
end $$;
drop trigger if exists limite_fiches on public.fiches;
create trigger limite_fiches before insert on public.fiches for each row execute function public.limite_fiches();
