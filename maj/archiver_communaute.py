#!/usr/bin/env python3
"""Archive anonyme et cumulative des données communautaires du serveur Supabase (releves, pastilles, fiches).

Pourquoi : le serveur supprime les relevés, fiches et pastilles après 3 ans (tâche pg_cron `purge-donnees`). Ce script copie les
colonnes publiques (jamais la colonne `device`) dans data/archive-communaute/*.json, en AJOUT SEUL : une ligne déjà archivée n'est
jamais supprimée, même si elle disparaît du serveur. Licence des données : ODbL 1.0 (voir DATA-LICENCE.md).
Effacement sur demande : les identifiants de ligne (client_id) listés dans data/archive-communaute/exclusions.json sont retirés de
l'archive et jamais réarchivés (voir PRIVACY.md). Le fichier ne contient que des identifiants de ligne, aucune autre donnée.
La clé ci-dessous est la clé PUBLIABLE de l'application (publique par conception ; accès limité par les règles de la base).
"""
import json, os, sys, urllib.request

URL = "https://evvoufjrzejumdpjgoek.supabase.co"
KEY = "sb_publishable_phgSpnbZ0xQSz93hzalZ9A_mWcgPrmx"
DEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data", "archive-communaute")
TABLES = {
    "releves": ("client_id,station_id,station_name,lat,lon,current,result,car,ts", "ts"),
    "pastilles": ("client_id,station_id,code,tested,created_at", "created_at"),
    "fiches": ("client_id,station_id,station_name,lat,lon,points,plugs,kw,pastille,created_at", "created_at"),
}
PAGE = 1000

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
    return set(json.load(open(path, encoding="utf-8")))

def main():
    os.makedirs(DEST, exist_ok=True)
    excl = exclusions()
    for table, (cols, sortkey) in TABLES.items():
        path = os.path.join(DEST, f"{table}.json")
        old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else []
        merged = {r["client_id"]: r for r in old if r["client_id"] not in excl}
        new = fetch(table, cols)
        for r in new:
            if "device" in r:
                sys.exit("colonne device présente : arrêt")
            if r["client_id"] not in excl:
                merged.setdefault(r["client_id"], r)
        out = sorted(merged.values(), key=lambda r: (str(r.get(sortkey, "")), r["client_id"]))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print(f"{table} : {len(new)} sur le serveur, {len(out)} archivés")

if __name__ == "__main__":
    main()
