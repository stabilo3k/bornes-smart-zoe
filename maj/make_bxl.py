#!/usr/bin/env python3
"""Télécharge les bornes de Bruxelles (data.mobility.brussels, CC BY, Paradigm / Eco-Movement)
et écrit un fichier compact bornes-bruxelles.json. Usage : make_bxl.py sortie.json"""
import json, sys, urllib.request, datetime
URL = ("https://data.mobility.brussels/geoserver/bm_equipment/wfs?service=WFS&version=2.0.0&request=GetFeature"
       "&typeNames=bm_equipment:evse&outputFormat=application/json&srsName=EPSG:4326")
d = json.load(urllib.request.urlopen(urllib.request.Request(URL, headers={"User-Agent": "BorneNeutre-maj/1.0"}), timeout=90))
out = []
for f in d["features"]:
    p = f["properties"]; x, y = f["geometry"]["coordinates"]
    out.append({"i": p["location_id"], "o": p.get("operator_name") or "", "la": round(y, 5), "lo": round(x, 5),
                "p": p.get("charging_points") or 0, "c": p.get("connector_type") or "", "t": p.get("location_type") or ""})
out.sort(key=lambda r: r["i"])
json.dump({"generator": "data.mobility.brussels bm_equipment:evse (Paradigm / Eco-Movement), CC BY",
           "date": datetime.date.today().isoformat(), "count": len(out), "stations": out},
          open(sys.argv[1], "w"), ensure_ascii=False, separators=(",", ":"))
print(len(out), "bornes")
