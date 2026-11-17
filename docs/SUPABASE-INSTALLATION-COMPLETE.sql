-- BorneNeutre : installation complète (v1 + v2 + liste de voitures ouverte). À coller UNE fois dans Supabase > SQL Editor > Run. Peut être relancé sans danger.
create table if not exists public.releves (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique check (char_length(client_id) between 3 and 60),
  device text not null check (char_length(device) between 8 and 64),
  station_id text not null check (char_length(station_id) between 1 and 80),
  station_name text not null default '' check (char_length(station_name) <= 120),
  lat double precision not null check (lat between 49.4 and 51.6),
  lon double precision not null check (lon between 2.5 and 6.5),
  current text not null default '?' check (current in ('AC','DC','?')),
  result text not null check (result in ('OK','REFUS','LIMITE')),
  car text not null check (char_length(car) between 2 and 40),
  ts date not null check (ts between date '2025-01-01' and current_date + 1),
  created_at timestamptz not null default now()
);
alter table public.releves enable row level security;
drop policy if exists "lecture" on public.releves;
create policy "lecture" on public.releves for select to anon using (true);
drop policy if exists "ajout" on public.releves;
create policy "ajout" on public.releves for insert to anon with check (true);
revoke all on public.releves from anon;
grant select (client_id, station_id, station_name, lat, lon, current, result, car, ts) on public.releves to anon;
grant insert on public.releves to anon;
create or replace function public.limite_releves() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.releves where device = new.device and created_at > now() - interval '24 hours') >= 40 then
    raise exception 'limite quotidienne atteinte';
  end if;
  if (select count(*) from public.releves where created_at > now() - interval '1 hour') >= 600 then
    raise exception 'limite globale atteinte';
  end if;
  return new;
end $$;
drop trigger if exists limite_releves on public.releves;
create trigger limite_releves before insert on public.releves for each row execute function public.limite_releves();
