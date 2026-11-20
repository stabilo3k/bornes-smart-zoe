#!/usr/bin/env python3
"""Bornes de Wallonie : télécharge le CSV officiel du SPW (« Infrastructures de recharge pour véhicules électriques en Wallonie »,
CC BY 4.0, source à citer) et écrit une liste compacte, un enregistrement par emplacement (format lu par Wallonie.kt).
Coordonnées Lambert 72 (EPSG:31370) converties en WGS84 (nécessite pyproj).
Usage : make_wal.py sortie.json [fichier.csv]   (sans fichier.csv, téléchargement unique depuis geoservices.wallonie.be)"""
import csv, io, json, re, sys, datetime, urllib.request
from collections import defaultdict
from pyproj import Transformer

URL = ("https://geoservices.wallonie.be/geotraitement/spwdatadownload/results/"
       "c731a282-e378-49f3-8ab6-f9a90e7a5683/BORNES_RECHARGE_XY_31370.csv")
# Mention exacte de l'onglet « Conditions d'utilisation » de la fiche (la fiche donne aussi (2026-04-10) dans « Citer la ressource ») : à reprendre telle quelle (CC BY 4.0)
SOURCE = "Source : Service public de Wallonie (SPW) - Infrastructures de recharge pour véhicules électriques en Wallonie (2026-01-30)"

def lire(chemin):
    if chemin:
        return open(chemin, encoding="utf-8-sig", newline="").read()
    req = urllib.request.Request(URL, headers={"User-Agent": "BorneNeutre-maj/1.0 (+https://github.com/stabilo3k/bornes-smart-zoe)"})
    return urllib.request.urlopen(req, timeout=120).read().decode("utf-8-sig")

def main():
    sortie = sys.argv[1]; chemin = sys.argv[2] if len(sys.argv) > 2 else None
    rows = list(csv.DictReader(io.StringIO(lire(chemin)), delimiter=";"))
    tr = Transformer.from_crs("EPSG:31370", "EPSG:4326", always_xy=True)
    groupes = defaultdict(list)
    for r in rows: groupes[r["EMPLACEMENT_ID"] or "x" + r["OBJECTID"]].append(r) # identifiant manquant : un emplacement par ligne
    dates = sorted({r.get("DATETRANS", "")[:10] for r in rows if r.get("DATETRANS")})
    out = []
    for eid, lst in groupes.items():
        r0 = lst[0]
        m = re.match(r"POINT \(([-\d.]+) ([-\d.]+)\)", r0.get("WKT_GEOM", ""))
        if not m: continue
        lo, la = tr.transform(float(m.group(1)), float(m.group(2)))
        kw = max((float(x["PUISSANCE_KW"]) for x in lst if x["PUISSANCE_KW"]), default=0.0)
        adr = " ".join(x for x in (r0["ADRESSE"], r0["CODE_POSTAL"], r0["VILLE"]) if x).strip()
        out.append({"i": ("o" + eid[1:] if eid.startswith("x") else eid.replace("-", "")[:8] + eid[-4:]), "la": round(la, 5), "lo": round(lo, 5), "o": r0["OPERATEUR"], "a": adr,
                    "s": 1 if r0["TYPE_ACCES"] == "semi-public" else 0, "p": len(lst), "kw": kw,
                    "c": "DC" if any(x["TYPE_RECHARGE"] in ("Rapide", "Ultra-rapide") for x in lst) else "AC"})
    out.sort(key=lambda r: r["i"])
    json.dump({"source": SOURCE, "donnees": dates[-1] if dates else "", "extrait": datetime.date.today().isoformat(),
               "count": len(out), "points": sum(r["p"] for r in out), "stations": out},
              open(sortie, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(out), "emplacements,", sum(r["p"] for r in out), "points, données du", dates[-1] if dates else "?")

if __name__ == "__main__":
    main()
