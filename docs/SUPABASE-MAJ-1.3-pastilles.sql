-- BorneNeutre 1.3 : table « pastilles » (constat visuel de la pastille collée sur une borne).
-- À coller une fois dans Supabase > SQL Editor > Run. Sans elle, l'application garde les observations sur le téléphone.
-- Séparée de « releves » : jamais comptée dans « ça charge / ne charge pas ». Aucune position de l'utilisateur ; lat/lon = celles de la borne.
create table if not exists public.pastilles (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique check (char_length(client_id) between 3 and 60),
  device text not null check (char_length(device) between 8 and 64),
  station_id text not null check (char_length(station_id) between 1 and 80),
  station_name text not null default '' check (char_length(station_name) <= 120),
  lat double precision not null check (lat between 49.4 and 51.6),
  lon double precision not null check (lon between 2.5 and 6.5),
  code text not null check (code in ('verte','blanche','aucune')),
  tested boolean not null default false,
  created_at timestamptz not null default now()
);
alter table public.pastilles enable row level security;
drop policy if exists "lecture" on public.pastilles;
create policy "lecture" on public.pastilles for select to anon using (true);
drop policy if exists "ajout" on public.pastilles;
create policy "ajout" on public.pastilles for insert to anon with check (true);
revoke all on public.pastilles from anon;
-- Lecture limitée aux colonnes publiques : « device » (identifiant du téléphone) n'est jamais lisible. Insertion ouverte.
-- Correctif du 20/11/2026 : la fonction de limite ci-dessous est « security definer » (elle lit « device » en dehors des droits de l'appelant) ; sans cela, la lecture limitée faisait refuser l'envoi (HTTP 401, code 42501).
grant select (id, client_id, station_id, station_name, lat, lon, code, tested, created_at) on public.pastilles to anon;
grant insert on public.pastilles to anon;
-- Une pastille par téléphone, borne, code et jour.
create unique index if not exists pastilles_une_par_jour on public.pastilles (device, station_id, code, ((created_at at time zone 'Europe/Brussels')::date));
-- Limite anti-abus : 60 pastilles par téléphone et par 24 h.
create or replace function public.limite_pastilles() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.pastilles where device = new.device and created_at > now() - interval '24 hours') >= 60 then
    raise exception 'limite quotidienne atteinte';
  end if;
  return new;
end $$;
drop trigger if exists limite_pastilles on public.pastilles;
create trigger limite_pastilles before insert on public.pastilles for each row execute function public.limite_pastilles();
