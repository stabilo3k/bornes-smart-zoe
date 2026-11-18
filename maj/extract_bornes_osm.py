#!/usr/bin/env python3
"""Construit data/bornes-be-osm.json (bornes de recharge de Belgique, OpenStreetMap, ODbL)
sans dépendre d'Overpass.

Deux entrées possibles :
  1) --geojsonseq f.geojsonl  : sortie de `osmium export -f geojsonseq` d'un extrait filtré
     (voir .github/workflows/maj-osm.yml : extrait Geofabrik de la Belgique) ;
  2) --overpass f.json        : export Overpass brut (`out center tags;`), par exemple depuis overpass-turbo.
Sortie : fichier au format de l'application {generator, copyright, elements:[{type,id,tags,lat,lon | center}]}
et mise à jour de meta.json (date). Usage : extract_bornes_osm.py (--geojsonseq|--overpass) entrée.json sortie.json [meta.json]
"""
import json, sys, datetime

def centre(geom):
    t, c = geom["type"], geom["coordinates"]
    pts = []
    def walk(x):
        if x and isinstance(x[0], (int, float)): pts.append(x)
        else:
            for y in x: walk(y)
    walk(c)
    return (sum(p[1] for p in pts) / len(pts), sum(p[0] for p in pts) / len(pts)) if pts else None

def depuis_geojsonseq(chemin):
    out = []
    for ligne in open(chemin, encoding="utf-8"):
        ligne = ligne.strip().lstrip("\x1e")
        if not ligne: continue
        f = json.loads(ligne)
        tags = f.get("properties") or {}
        if tags.get("amenity") != "charging_station": continue
        fid = str(f.get("id", ""))          # « n123 », « w456 », « r789 » ou « a… » (surface osmium : 2×id de voie, ou 2×id de relation + 1)
        if not fid or fid[0] not in "nwra" or not fid[1:].isdigit(): continue
        typ, num = {"n": "node", "w": "way", "r": "relation"}.get(fid[0]), int(fid[1:])
        if fid[0] == "a":
            typ, num = ("way", num // 2) if num % 2 == 0 else ("relation", num // 2)
        c = centre(f["geometry"]) if f.get("geometry") else None
        if not c: continue
        e = {"type": typ, "id": num, "tags": tags}
        if typ == "node": e["lat"], e["lon"] = round(c[0], 7), round(c[1], 7)
        else: e["center"] = {"lat": round(c[0], 7), "lon": round(c[1], 7)}
        out.append(e)
    return out

def depuis_overpass(chemin):
    d = json.load(open(chemin, encoding="utf-8"))
    return [e for e in d.get("elements", []) if (e.get("tags") or {}).get("amenity") == "charging_station"
            and (("lat" in e and "lon" in e) or "center" in e)]

def main(a):
    if len(a) < 3 or a[0] not in ("--geojsonseq", "--overpass"):
        sys.exit(__doc__)
    els = depuis_geojsonseq(a[1]) if a[0] == "--geojsonseq" else depuis_overpass(a[1])
    uniques = {}
    for e in els: uniques.setdefault((e["type"], e["id"]), e)   # osmium exporte parfois une voie deux fois (ligne et surface)
    els = sorted(uniques.values(), key=lambda e: (e["type"], e["id"]))
    if len(els) < 3000:                      # garde-fou : la Belgique compte plus de 6 000 bornes (liste du 17/11/2026)
        sys.exit("Trop peu de bornes (%d) : extraction refusée, fichier non écrit." % len(els))
    aujourdhui = datetime.date.today()
    json.dump({"generator": "extraits OpenStreetMap (Geofabrik, Belgique), %s" % aujourdhui.strftime("%d/%m/%Y"),
               "copyright": "© OpenStreetMap contributors, ODbL 1.0", "elements": els},
              open(a[2], "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    if len(a) > 3:
        m = json.load(open(a[3], encoding="utf-8")); m["date"] = aujourdhui.isoformat()
        json.dump(m, open(a[3], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(len(els), "bornes")

if __name__ == "__main__":
    main(sys.argv[1:])
