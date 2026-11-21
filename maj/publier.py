#!/usr/bin/env python3
"""Dernière étape de la mise à jour des listes (après l'extraction OSM, Bruxelles et Wallonie).
Pour chaque source :
  1) garde-fou relatif : si le nombre d'éléments tombe de plus de 10 % ou monte de plus de 25 % par rapport au fichier précédent, l'ANCIEN fichier est rétabli ;
  2) registre de première vue data/vues.json (par identifiant ; une borne neuve à moins de 25 m d'une borne déjà vue de la même source hérite de sa date : pas de « nouveauté » due à un simple changement d'identifiant) ;
  3) champ « d » (date d'entrée dans NOTRE liste, AAAA-MM-JJ) écrit au niveau de l'élément, jamais dans les étiquettes, pour les entrées de moins de 60 jours (l'application affiche 45 jours) ;
  4) data/meta.json : empreinte sha256 et nombre d'éléments de chaque fichier ; data/etat.json : état de chaque source.
Première exécution (registre vide) : toutes les bornes reçoivent une date ancienne (1970-01-01) : aucune n'est annoncée comme « ajoutée ».
Sortie : code 1 si un garde-fou a rétabli un fichier (la publication des autres fichiers continue). Usage : publier.py dossier_des_copies_précédentes"""
import datetime, hashlib, json, math, os, shutil, sys

DATA = "data"
BASE = "1970-01-01"
JOURS = 60
SOURCES = {
    "osm": ("bornes-be-osm.json", "elements"),
    "bxl": ("bornes-bruxelles.json", "stations"),
    "wal": ("bornes-wallonie.json", "stations"),
}

def cle_pos(src, e):
    if src == "osm":
        c = e.get("center") or e
        return "%s%s" % (e["type"][0], e["id"]), c.get("lat"), c.get("lon")
    return e["i"], e.get("la"), e.get("lo")

def dist_m(la1, lo1, la2, lo2):
    k = 111000.0
    return math.hypot((la1 - la2) * k, (lo1 - lo2) * k * math.cos(math.radians(la1)))

def charge(chemin):
    return json.load(open(chemin, encoding="utf-8"))

def ecrit(chemin, d):
    json.dump(d, open(chemin, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

def main():
    prev = sys.argv[1] if len(sys.argv) > 1 else None
    aujourdhui = datetime.date.today()
    limite = (aujourdhui - datetime.timedelta(days=JOURS)).isoformat()
    vues_p = os.path.join(DATA, "vues.json")
    vues = charge(vues_p) if os.path.exists(vues_p) else {}
    vues["version"] = 1; vues["jours_visibles"] = 45
    etat = {"date": aujourdhui.isoformat(), "sources": {}}
    meta_p = os.path.join(DATA, "meta.json")
    meta = charge(meta_p)
    refus = False
    for src, (nom, cle) in SOURCES.items():
        chemin = os.path.join(DATA, nom)
        if not os.path.exists(chemin):
            etat["sources"][src] = {"statut": "absent"}; continue
        d = charge(chemin)
        n = len(d[cle])
        avant = None
        if prev and os.path.exists(os.path.join(prev, nom)):
            try: avant = len(charge(os.path.join(prev, nom))[cle])
            except Exception: avant = None
        if avant and not (0.90 * avant <= n <= 1.25 * avant):
            shutil.copyfile(os.path.join(prev, nom), chemin)       # rétablit l'ancien fichier
            etat["sources"][src] = {"statut": "refuse", "motif": "variation hors −10 % / +25 %", "avant": avant, "recu": n}
            refus = True
            d = charge(chemin); n = len(d[cle])
            print("REFUS %s : %d -> %d, ancien fichier rétabli" % (src, avant, etat["sources"][src]["recu"]))
        else:
            etat["sources"][src] = {"statut": "ok", "n": n, "avant": avant}
        # registre de première vue (mis à jour même pour un fichier rétabli : il ne gagne rien de neuf)
        reg = vues.setdefault(src, {})
        premiere = not reg
        grille = {}
        def cell(la, lo): return (int(la / 0.0003), int(lo / 0.0003))
        if not premiere:
            for k, (dt, la, lo) in reg.items():
                if la is not None: grille.setdefault(cell(la, lo), []).append((dt, la, lo))
        for e in d[cle]:
            k, la, lo = cle_pos(src, e)
            if k in reg: continue
            if premiere or la is None: reg[k] = [BASE if premiere else aujourdhui.isoformat(), la, lo]; continue
            date = None
            cy, cx = cell(la, lo)
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    for dt, la2, lo2 in grille.get((cy + dy, cx + dx), ()):
                        if dist_m(la, lo, la2, lo2) <= 25.0 and (date is None or dt < date): date = dt
            reg[k] = [date or aujourdhui.isoformat(), la, lo]
        for e in d[cle]:
            k, _, _ = cle_pos(src, e)
            dt = reg[k][0]
            if dt >= limite: e["d"] = dt
            else: e.pop("d", None)
        ecrit(chemin, d)
    ecrit(vues_p, vues)
    meta["files"] = {}
    for src, (nom, cle) in SOURCES.items():
        chemin = os.path.join(DATA, nom)
        if os.path.exists(chemin):
            b = open(chemin, "rb").read()
            meta["files"][nom] = {"sha256": hashlib.sha256(b).hexdigest(), "n": len(json.loads(b.decode("utf-8"))[cle])}
    json.dump(meta, open(meta_p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.dump(etat, open(os.path.join(DATA, "etat.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps(etat, ensure_ascii=False))
    sys.exit(1 if refus else 0)

if __name__ == "__main__":
    main()
