-- BorneNeutre v0.8 : à coller UNE fois dans Supabase > SQL Editor > Run (après supabase.sql déjà exécuté).
-- 1) La lecture publique ne montre plus l'identifiant du téléphone ni l'heure d'ajout.
revoke select on public.releves from anon;
grant select (client_id, station_id, station_name, lat, lon, current, result, car, ts) on public.releves to anon;

-- 2) Limites anti-abus : 40 relevés par téléphone et par 24 h, 600 au total par heure.
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
