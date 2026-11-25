#!/usr/bin/env python3
"""Archive anonyme et cumulative des données communautaires du serveur Supabase (releves, pastilles, fiches).

Pourquoi : le serveur supprime les relevés, fiches et pastilles après 3 ans (tâche pg_cron `purge-donnees`). Ce script copie les
colonnes publiques (jamais la colonne `device`) dans data/archive-communaute/*.json, en AJOUT SEUL : une ligne déjà archivée n'est
jamais supprimée, même si elle disparaît du serveur. Licence des données : ODbL 1.0 (voir DATA-LICENCE.md).
Effacement sur demande : les identifiants de ligne (client_id) listés dans data/archive-communaute/exclusions.json sont retirés de
l'archive et jamais réarchivés (voir PRIVACY.md). Le fichier ne contient que des identifiants de ligne, aucune autre donnée.
La clé ci-dessous est la clé PUBLIABLE de l'application (publique par conception ; accès limité par les règles de la base).
"""
import datetime, json, os, re, sys, urllib.request

URL = "https://evvoufjrzejumdpjgoek.supabase.co"
KEY = "sb_publishable_phgSpnbZ0xQSz93hzalZ9A_mWcgPrmx"
DEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data", "archive-communaute")
TABLES = {
    "releves": ("client_id,station_id,station_name,lat,lon,current,result,car,ts", "ts"),
    "pastilles": ("client_id,station_id,code,tested,created_at", "created_at"),
    "fiches": ("client_id,station_id,station_name,lat,lon,points,plugs,kw,pastille,created_at", "created_at"),
}
PAGE = 1000
CARS = {"Smart EQ fortwo", "Smart EQ forfour", "Renault Zoé", "Renault Twingo électrique", "Renault Kangoo E-Tech", "Nissan Townstar EV", "Mercedes eCitan"}
CODES = {"verte", "bleue", "blanche", "aucune", "autre", "bleue_22_400", "bleue_74_230", "bleue_autre", "bleue_11_400"}  # = contraintes du serveur (pastilles.code, fiches.pastille) ; à tenir alignées
CTRL = re.compile(r"[\x00-\x1f\x7f<>]")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}")

def _txt(v, n=200):
    return v is None or (isinstance(v, str) and len(v) <= n and not CTRL.search(v))

def _geo(r):
    return isinstance(r.get("lat"), (int, float)) and isinstance(r.get("lon"), (int, float)) and 35 <= r["lat"] <= 72 and -12 <= r["lon"] <= 45

def valide(table, r):
    """Contrôle de forme avant archivage (jamais de correction : une ligne douteuse est écartée et comptée, pas recopiée).
    Les lignes valides par la base ont déjà passé ses contraintes ; ceci protège l'archive PUBLIQUE contre des valeurs hors liste, des textes
    de plus de 200 caractères ou contenant des caractères de contrôle, des coordonnées hors d'Europe et des dates dans le futur."""
    demain = (datetime.date.today() + datetime.timedelta(days=1)).isoformat()
    if not (isinstance(r.get("client_id"), str) and 0 < len(r["client_id"]) <= 80 and not CTRL.search(r["client_id"])):
        return False
    if not (_txt(r.get("station_id")) and _txt(r.get("station_name"))):
        return False
    d = str(r.get("ts") if table == "releves" else r.get("created_at") or "")
    if not DATE.match(d) or d[:10] > demain:
        return False
    if table == "releves":
        return _geo(r) and r.get("result") in ("OK", "REFUS", "LIMITE") and r.get("current") in (None, "", "AC", "DC", "?") and r.get("car") in CARS | {None, ""}
    if table == "pastilles":
        return r.get("code") in CODES and isinstance(r.get("tested"), bool)
    return _geo(r) and (r.get("points") is None or isinstance(r["points"], int)) and (r.get("plugs") is None or isinstance(r["plugs"], int)) and (r.get("kw") is None or isinstance(r["kw"], (int, float))) \
        and (r.get("pastille") is None or r["pastille"] in CODES)

def minimiser(table, r):
    """Minimisation avant publication (25/11/2026) : la date est tronquée au jour (jamais l'heure : avec peu de contributeurs, des horaires et des
    lieux publics décrivent les déplacements d'une personne) et la position de la borne est arrondie à 5 décimales (environ 1 m)."""
    r = dict(r)
    if table != "releves" and isinstance(r.get("created_at"), str):
        r["created_at"] = r["created_at"][:10]
    if isinstance(r.get("ts"), str):
        r["ts"] = r["ts"][:10]
    for k in ("lat", "lon"):
        if isinstance(r.get(k), (int, float)):
            r[k] = round(r[k], 5)
    return r

def fetch(table, cols):
    rows, start = [], 0
    while True:
        req = urllib.request.Request(
            f"{URL}/rest/v1/{table}?select={cols}&order=client_id.asc",
            headers={"apikey": KEY, "Authorization": f"Bearer {KEY}", "Range-Unit": "items", "Range": f"{start}-{start + PAGE - 1}"})
        with urllib.request.urlopen(req, timeout=60) as r:
            part = json.load(r)
        if not isinstance(part, list):
            sys.exit(f"réponse inattendue pour {table}")
        rows += part
        if len(part) < PAGE:
            return rows
        start += PAGE

def exclusions():
    path = os.path.join(DEST, "exclusions.json")
    if not os.path.exists(path):
        return set()
    # liste de chaînes (client_id) ou d'objets {"client_id": ...} : les deux formes sont acceptées
    return {e["client_id"] if isinstance(e, dict) else e for e in json.load(open(path, encoding="utf-8"))}

def main():
    os.makedirs(DEST, exist_ok=True)
    excl = exclusions()
    total_ecartees = 0
    for table, (cols, sortkey) in TABLES.items():
        path = os.path.join(DEST, f"{table}.json")
        old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else []
        merged = {r["client_id"]: minimiser(table, r) for r in old if r["client_id"] not in excl}
        new = fetch(table, cols)
        ecartees = 0
        for r in new:
            if "device" in r:
                sys.exit("colonne device présente : arrêt")
            if r["client_id"] in excl:
                continue
            if not valide(table, r):
                ecartees += 1
                total_ecartees += 1
                print(f"::warning::{table} : ligne écartée (forme invalide), client_id={str(r.get('client_id'))[:40]!r}")
                continue
            merged.setdefault(r["client_id"], minimiser(table, r))
        out = sorted(merged.values(), key=lambda r: (str(r.get(sortkey, "")), r["client_id"]))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print(f"{table} : {len(new)} sur le serveur, {len(out)} archivés, {ecartees} écartés (forme invalide)")
    if total_ecartees:
        # code 3 : l'archive est écrite et publiée par le workflow, puis le workflow échoue pour alerter (une ligne légitime a pu être écartée)
        sys.exit(3)

if __name__ == "__main__":
    main()
