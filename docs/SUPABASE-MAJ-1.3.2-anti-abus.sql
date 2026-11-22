-- BorneNeutre 1.3.2 : plafonds anti-abus côté serveur (préparé le 21/11/2026, NON exécuté tant que le mainteneur n'a pas donné son accord pour ce script).
-- À coller dans Supabase > SQL Editor > Run. Peut être relancé sans danger (create or replace, drop if exists).
-- Contexte : le champ « device » est choisi par l'application ; un script qui change d'identifiant à chaque envoi contourne les limites par téléphone.
-- Les plafonds ci-dessous ne dépendent donc pas de « device » : global par heure, par borne et par jour, et nombre d'identifiants neufs par heure.
-- Les erreurs des déclencheurs ne sont pas des erreurs de contrainte (code 23xxx) : l'application les traite comme « à renvoyer plus tard » (Sync.kt), aucun relevé légitime n'est perdu.
-- Valeurs choisies très au-dessus de l'usage réel (27 Mo de base, quelques utilisateurs) ; à ajuster après la campagne de décembre-mars.

-- 1) Relevés : 40 par téléphone et par 24 h (inchangé), 600 par heure au total (inchangé),
--    + 30 par borne et par jour, + au plus 100 identifiants de téléphone différents par heure.
create or replace function public.limite_releves() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.releves where device = new.device and created_at > now() - interval '24 hours') >= 40 then
    raise exception 'limite quotidienne atteinte';
  end if;
  if (select count(*) from public.releves where created_at > now() - interval '1 hour') >= 600 then
    raise exception 'limite globale atteinte';
  end if;
  if (select count(*) from public.releves where station_id = new.station_id and created_at > now() - interval '24 hours') >= 30 then
    raise exception 'limite par borne atteinte';
  end if;
  if (select count(distinct device) from public.releves where created_at > now() - interval '1 hour') >= 100
     and not exists (select 1 from public.releves where device = new.device and created_at > now() - interval '1 hour') then
    raise exception 'trop de nouveaux identifiants';
  end if;
  return new;
end $$;

-- 2) Pastilles : 60 par téléphone et par 24 h (inchangé), + 300 par heure au total, + 30 par borne et par jour, + 100 identifiants par heure.
create or replace function public.limite_pastilles() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.pastilles where device = new.device and created_at > now() - interval '24 hours') >= 60 then
    raise exception 'limite quotidienne atteinte';
  end if;
  if (select count(*) from public.pastilles where created_at > now() - interval '1 hour') >= 300 then
    raise exception 'limite globale atteinte';
  end if;
  if (select count(*) from public.pastilles where station_id = new.station_id and created_at > now() - interval '24 hours') >= 30 then
    raise exception 'limite par borne atteinte';
  end if;
  if (select count(distinct device) from public.pastilles where created_at > now() - interval '1 hour') >= 100
     and not exists (select 1 from public.pastilles where device = new.device and created_at > now() - interval '1 hour') then
    raise exception 'trop de nouveaux identifiants';
  end if;
  return new;
end $$;

-- 3) Fiches : 40 par téléphone et par 24 h (inchangé), + 200 par heure au total, + 20 par borne et par jour, + 100 identifiants par heure.
create or replace function public.limite_fiches() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.fiches where device = new.device and created_at > now() - interval '24 hours') >= 40 then
    raise exception 'limite quotidienne atteinte';
  end if;
  if (select count(*) from public.fiches where created_at > now() - interval '1 hour') >= 200 then
    raise exception 'limite globale atteinte';
  end if;
  if (select count(*) from public.fiches where station_id = new.station_id and created_at > now() - interval '24 hours') >= 20 then
    raise exception 'limite par borne atteinte';
  end if;
  if (select count(distinct device) from public.fiches where created_at > now() - interval '1 hour') >= 100
     and not exists (select 1 from public.fiches where device = new.device and created_at > now() - interval '1 hour') then
    raise exception 'trop de nouveaux identifiants';
  end if;
  return new;
end $$;

-- 4) Vue publique pour un verdict par téléphones DISTINCTS : par borne et par résultat, le nombre de relevés et le nombre de téléphones différents.
--    L'identifiant de téléphone n'est jamais exposé (seul un compte l'est). L'application pourra exiger 2 téléphones avant le vert « vérifié » (décision du mainteneur à prendre).
--    Les identifiants effacés par la purge (préfixe « efface- ») comptent chacun pour un téléphone : acceptable (les relevés de plus de 12 mois sont rares).
create or replace view public.releves_par_borne as
  select station_id, car, result, count(*) as n_releves, count(distinct device) as n_telephones, max(ts) as dernier
  from public.releves group by station_id, car, result;
alter view public.releves_par_borne set (security_invoker = false);
revoke all on public.releves_par_borne from anon;
grant select on public.releves_par_borne to anon;

-- 5) Détection d'anomalies pour le workflow supabase-reveil : fonction qui ne renvoie que des comptes (aucun identifiant de téléphone).
create or replace function public.anomalies() returns jsonb language sql security definer set search_path = public stable as $$
  select jsonb_build_object(
    'releves_1h',   (select count(*) from public.releves   where created_at > now() - interval '1 hour'),
    'pastilles_1h', (select count(*) from public.pastilles where created_at > now() - interval '1 hour'),
    'fiches_1h',    (select count(*) from public.fiches    where created_at > now() - interval '1 hour'),
    'releves_24h',  (select count(*) from public.releves   where created_at > now() - interval '24 hours'),
    'pastilles_24h',(select count(*) from public.pastilles where created_at > now() - interval '24 hours'),
    'fiches_24h',   (select count(*) from public.fiches    where created_at > now() - interval '24 hours'),
    'telephones_neufs_24h', (select count(distinct device) from public.releves r
                              where created_at > now() - interval '24 hours'
                                and not exists (select 1 from public.releves o where o.device = r.device and o.created_at <= now() - interval '24 hours')),
    'borne_max_releves_24h', (select coalesce(max(c),0) from (select count(*) c from public.releves where created_at > now() - interval '24 hours' group by station_id) t),
    'borne_max_pastilles_24h', (select coalesce(max(c),0) from (select count(*) c from public.pastilles where created_at > now() - interval '24 hours' group by station_id) t),
    'telephone_max_releves_24h', (select coalesce(max(c),0) from (select count(*) c from public.releves where created_at > now() - interval '24 hours' group by device) t)
  );
$$;
revoke all on function public.anomalies() from public;
grant execute on function public.anomalies() to anon;

-- 6) Procédure de purge d'urgence (à exécuter par le mainteneur dans l'éditeur SQL, jamais automatiquement ; sauvegarder d'abord : exporter les tables en CSV).
--    a) Repérer la rafale : select date_trunc('hour', created_at) h, count(*) from public.releves group by 1 order by 1 desc limit 24;
--    b) Voir les identifiants suspects (visible seulement ici, avec les droits du propriétaire) :
--       select device, count(*), min(created_at), max(created_at) from public.releves where created_at > now() - interval '24 hours' group by device order by 2 desc limit 30;
--    c) Supprimer une rafale précise (remplacer les bornes de temps ; vérifier le compte avant) :
--       select count(*) from public.releves where created_at between '2026-12-01 10:00+01' and '2026-12-01 11:00+01';
--       delete from public.releves where created_at between '2026-12-01 10:00+01' and '2026-12-01 11:00+01';
--       (même chose pour public.pastilles et public.fiches)
--    d) Supprimer tout ce qui vient d'un identifiant : delete from public.releves where device = '<identifiant>';
--    e) En cas d'attaque continue : suspendre l'écriture publique le temps de nettoyer :
--       revoke insert on public.releves, public.pastilles, public.fiches from anon;   -- puis, après nettoyage :
--       grant insert on public.releves, public.pastilles, public.fiches to anon;
--       (les téléphones gardent leurs relevés en file d'attente et les renvoient plus tard)
