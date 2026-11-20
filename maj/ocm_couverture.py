#!/usr/bin/env python3
"""Contrôle de couverture : compare les bornes belges d'Open Charge Map (licences ouvertes seulement) à notre liste OpenStreetMap.
Ne publie que des chiffres et des identifiants OCM (aucune donnée de fournisseur recopiée). Clé API lue dans la variable d'environnement OCM_API_KEY (secret GitHub), jamais écrite dans un fichier.
Usage : ocm_couverture.py data/bornes-be-osm.json data/couverture-ocm.json
Données : © Open Charge Map et ses fournisseurs de données (licences ouvertes), https://openchargemap.org"""
import json, math, os, sys, datetime, urllib.request, urllib.parse

def grille(points, cote=0.001):
    g = {}
    for la, lo in points:
        g.setdefault((round(la / cote), round(lo / cote)), []).append((la, lo))
    return g

def dist_m(a, b):
    dla = (a[0] - b[0]) * 111320.0
    dlo = (a[1] - b[1]) * 111320.0 * math.cos(math.radians(a[0]))
    return math.hypot(dla, dlo)

def proche(g, p, rayon=60.0, cote=0.001):
    ci, cj = round(p[0] / cote), round(p[1] / cote)
    for i in (ci - 1, ci, ci + 1):
        for j in (cj - 1, cj, cj + 1):
            for q in g.get((i, j), ()):
                if dist_m(p, q) <= rayon:
                    return True
    return False

def main(osm_path, sortie):
    cle = os.environ.get("OCM_API_KEY", "")
    if not cle:
        print("OCM_API_KEY absente : contrôle ignoré"); return 0
    osm = json.load(open(osm_path, encoding="utf-8"))
    pts = []
    for e in osm["elements"]:
        la, lo = e.get("lat"), e.get("lon")
        if la is None and e.get("center"): la, lo = e["center"]["lat"], e["center"]["lon"]
        if la is not None: pts.append((la, lo))
    g = grille(pts)
    q = urllib.parse.urlencode({"output": "json", "countrycode": "BE", "opendata": "true", "compact": "true", "verbose": "false", "maxresults": 10000})
    req = urllib.request.Request("https://api.openchargemap.io/v3/poi/?" + q, headers={"X-API-Key": cle, "User-Agent": "BorneNeutre-maj/1.0 (+https://github.com/stabilo3k/bornes-smart-zoe)"})
    with urllib.request.urlopen(req, timeout=120) as r:
        poi = json.load(r)
    absents, total = [], 0
    for p in poi:
        a = p.get("AddressInfo") or {}
        la, lo = a.get("Latitude"), a.get("Longitude")
        if la is None or lo is None: continue
        total += 1
        if not proche(g, (la, lo)):
            absents.append(p.get("ID"))
    res = {"date": datetime.date.today().isoformat(), "source": "Open Charge Map (licences ouvertes, opendata=true), https://openchargemap.org",
           "ocm_belgique": total, "osm_elements": len(pts), "ocm_absents_de_osm_a_60_m": len(absents),
           "part_couverte": round(1 - len(absents) / total, 3) if total else None,
           "ids_ocm_absents": sorted(i for i in absents if i)[:500]}
    json.dump(res, open(sortie, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("OCM : %d bornes, %d absentes d'OSM à 60 m" % (total, len(absents)))
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
