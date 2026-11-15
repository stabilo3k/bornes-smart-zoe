-- BorneNeutre : relevés partagés. À coller une fois dans Supabase > SQL Editor > Run.
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
  car text not null check (car in ('Smart EQ fortwo','Smart EQ forfour','Renault Zoé','Renault Twingo électrique')),
  ts date not null check (ts between date '2025-01-01' and current_date + 1),
  created_at timestamptz not null default now()
);

alter table public.releves enable row level security;

-- Tout le monde peut lire et ajouter ; personne ne peut modifier ni effacer (aucune règle update/delete).
drop policy if exists "lecture" on public.releves;
create policy "lecture" on public.releves for select to anon using (true);
drop policy if exists "ajout" on public.releves;
create policy "ajout" on public.releves for insert to anon with check (true);

revoke all on public.releves from anon;
grant select, insert on public.releves to anon;

-- Limite anti-abus : 40 relevés par téléphone et par 24 h.
create or replace function public.limite_releves() returns trigger language plpgsql as $$
begin
  if (select count(*) from public.releves where device = new.device and created_at > now() - interval '24 hours') >= 40 then
    raise exception 'limite quotidienne atteinte';
  end if;
  return new;
end $$;
drop trigger if exists limite_releves on public.releves;
create trigger limite_releves before insert on public.releves for each row execute function public.limite_releves();
