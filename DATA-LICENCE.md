# Licences des données

## Code de l'application
GPL-3.0 (voir `LICENSE`).

## Bornes de recharge (position, exploitant, puissance, accès)
Liste embarquée dans l'application (`app/src/main/assets/bornes-be-osm.json`) : extraction de la **Belgique le 17/11/2026** depuis OpenStreetMap (extraits régionaux de openstreetmap.fr).
© contributeurs OpenStreetMap, licence **ODbL 1.0** : <https://www.openstreetmap.org/copyright>.
Toute réutilisation de cette liste doit citer « © contributeurs OpenStreetMap » et rester sous ODbL (partage à l'identique).
Seuls les champs utiles sont conservés (position, nom, exploitant, prises, puissance, accès, horaires).

## Bornes de Flandre (données ouvertes, chargées à la demande)
Jeu « Laadpunten voor elektrische voertuigen » du Departement Mobiliteit en Openbare Werken (Vlaamse overheid), source Eco-Movement, licence **Open Data Licentie Vlaanderen** (mention de la source obligatoire) : <https://metadata.vlaanderen.be/srv/api/records/d46516ca-c159-41bc-9c54-aa256c337228>.
L'application interroge le service WFS officiel autour de la position de l'utilisateur et garde une copie locale sur le téléphone ; ces données ne sont pas redistribuées avec l'application. Ne couvre ni Bruxelles ni la Wallonie.

## Bornes de Wallonie (liste embarquée)
« Infrastructures de recharge pour véhicules électriques en Wallonie », Service public de Wallonie (SPW), licence **CC BY 4.0**. Mention : « Source : Service public de Wallonie (SPW) - Infrastructures de recharge pour véhicules électriques en Wallonie (2026-01-30) ». Fichier `app/src/main/assets/bornes-wallonie.json` fabriqué le 19/11/2026 à partir du CSV officiel (copie locale ; le téléphone n'interroge jamais le service du SPW). Champs gardés : position, exploitant, adresse, accès, nombre de points, puissance maximale, courant continu ou alternatif d'après le type de recharge ; la source ne donne ni tension ni nombre de phases.

## Bornes de Bruxelles (liste embarquée)
Équipements de recharge de Bruxelles Mobilité (portail data.mobility.brussels, jeu « bm_equipment:evse », source Paradigm / Eco-Movement), fichier `app/src/main/assets/bornes-bruxelles.json` du 17/11/2026. Licence annoncée par le portail : **CC BY** (statut R : à re-vérifier sur la fiche du jeu avant publication ; le README indiquait auparavant « CC0, CC BY »). Mention : « Bruxelles Mobilité (data.mobility.brussels) / Eco-Movement ».

## Fond de carte
OpenFreeMap / OpenMapTiles, données © contributeurs OpenStreetMap.

## Relevés et signalements de la communauté
Relevés « Ça charge / Ne charge pas » (borne, résultat, date, modèle de voiture, puissance facultative) et états de borne (panne, occupée, file, libre, déclaré par un tiers) : envoyés anonymement par les utilisateurs, sans nom, plaque, trajet ni position GPS de l'utilisateur.
**Licence : ODbL 1.0** (décision du projet), avec mention de la source (« relevés de la communauté de l'application Bornes Smart et Zoé »). Les positions et noms de bornes issus d'OpenStreetMap y restent © contributeurs OpenStreetMap.
**Exception à « sans position » :** quand un utilisateur ajoute une nouvelle borne et choisit de la partager (case décochée par défaut), la position de cette borne, donc celle où il se trouvait, est envoyée. Une borne privée n'est jamais envoyée.
Les relevés ne sont pas des garanties : une borne qui a chargé une fois peut refuser ensuite.
Les signalements « déclaré, non vérifié » sont des témoignages non testés ; ils sont affichés à part et ne changent jamais le verdict.

## Pas de disponibilité en temps réel
L'application n'utilise aucun flux de disponibilité d'opérateur. Toute intégration future (par exemple transportdata.be, CC BY 4.0) citera sa source.

## Liste « 230 V » de TotalEnergies (anneaux gris, Bruxelles)
Liste d'adresses de bornes TotalEnergies / Charging Belgium identifiées « 230 V », transmise par leur support (ticket #38732) ; publication autorisée par écrit le 20/11/2026 à condition de préciser qu'il s'agit des informations issues de leurs données **en date du 16/11/2026**. TotalEnergies ne distingue pas le 230 V monophasé du 3×230 V : la liste ne dit pas qu'une voiture ne charge pas. Chaque adresse a été placée sur la carte par géocodage OpenStreetMap (ODbL), puis rapprochée de l'emplacement TotalEnergies le plus proche de la couche Bruxelles Mobilité (CC BY). Fichier : `app/src/main/java/org/borneneutre/app/Te230.kt`.

## Bornes « à essayer » (anneaux violets, Bruxelles)
Bornes de la concession de Bruxelles Mobilité classées « 400V - 22kW » (couche `charging_stations_concession` de data.mobility.brussels, CC BY, lue les 18 et 20/11/2026). Classification de conception du concessionnaire, pas une mesure. Fichier : `app/src/main/java/org/borneneutre/app/Essai400.kt`.
