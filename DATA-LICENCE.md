# Licences des données

## Code de l'application
GPL-3.0-or-later (voir `LICENSE`).

## Bornes de recharge (position, exploitant, puissance, accès)
Liste embarquée dans l'application (`app/src/main/assets/bornes-be-osm.json`) : extraction de la **Belgique le 20/11/2026** depuis OpenStreetMap (extraits Geofabrik d'abord ; mise à jour par le workflow du dépôt public, chaque jour).
© contributeurs OpenStreetMap, licence **ODbL 1.0** : <https://www.openstreetmap.org/copyright>.
Toute réutilisation de cette liste doit citer « © contributeurs OpenStreetMap » et rester sous ODbL (partage à l'identique).
Seuls les champs utiles sont conservés (position, nom, exploitant, prises, puissance, accès, horaires).

## Bornes de Flandre (données ouvertes, chargées à la demande)
Jeu « Laadpunten voor elektrische voertuigen » du Departement Mobiliteit en Openbare Werken (Vlaamse overheid), propriétaire Eco-Movement. Conditions lues sur la fiche du catalogue le 25/11/2026 (fiche modifiée le 23/07/2026) : réutilisation régie par la « Modellicentie voor gratis hergebruik » (Open Data Licentie Vlaanderen) ; **seule condition : la mention de la source** « Bron: Departement Mobiliteit en Openbare Werken » ; accès sans restriction (« geen beperkingen ») ; gratuit. Mention de la source reproduite dans l'application. (Le texte de la Modellicentie n'a pas été rouvert ; la question posée au Département le 17/11 reste sans réponse.) : <https://metadata.vlaanderen.be/srv/api/records/d46516ca-c159-41bc-9c54-aa256c337228>.
L'application interroge le service WFS officiel autour de la position de l'utilisateur et garde une copie locale sur le téléphone ; ces données ne sont pas redistribuées avec l'application. Ne couvre ni Bruxelles ni la Wallonie.

## Bornes de Wallonie (liste embarquée)
« Infrastructures de recharge pour véhicules électriques en Wallonie », Service public de Wallonie (SPW), licence **CC BY 4.0**. Mention : « Source : Service public de Wallonie (SPW) - Infrastructures de recharge pour véhicules électriques en Wallonie (2026-01-30) » (mention exacte de l'onglet « Conditions d'utilisation » de la fiche, relue le 25/11/2026 ; la rubrique « Citer la ressource » de la même fiche donne la forme « … (2026-04-10) » ; données transmises le 2026-04-10) Fichier `app/src/main/assets/bornes-wallonie.json` fabriqué à partir du CSV officiel (copie locale ; le téléphone n'interroge jamais le service du SPW) ; mis à jour par le workflow du dépôt public (script `maj/make_wal.py`) chaque dimanche, une requête par passage, en copie locale : le téléphone n'interroge jamais le SPW. Champs gardés : position, exploitant, adresse, accès, nombre de points, puissance maximale, courant continu ou alternatif d'après le type de recharge ; la source ne donne ni tension ni nombre de phases.

## Bornes de Bruxelles (liste embarquée)
Équipements de recharge de Bruxelles Mobilité (portail data.mobility.brussels, jeu « bm_equipment:evse », source Paradigm / Eco-Movement), fichier `app/src/main/assets/bornes-bruxelles.json` du 17/11/2026, mis à jour par le même workflow chaque dimanche. Licence annoncée par le portail : **CC BY** (statut R : à re-vérifier sur la fiche du jeu avant publication ; le README indiquait auparavant « CC0, CC BY »). Mention : « Bruxelles Mobilité (data.mobility.brussels) / Eco-Movement ».

## Anneaux violet et gris de la carte (Bruxelles)
Anneau violet : couche « bornes classées 400 V - 22 kW » de la concession de Bruxelles Mobilité (CC BY, lue les 18 et 20/11/2026 ; mention : « Bruxelles Mobilité (data.mobility.brussels) »). Anneau gris : liste « 230 V » transmise par le support de TotalEnergies / Charging Belgium, publication autorisée le 20/11/2026 sous la condition « données du 16/11/2026 ». Ce sont des hypothèses de conception, jamais des mesures.

## Fond de carte
OpenFreeMap / OpenMapTiles, données © contributeurs OpenStreetMap.

## Relevés et signalements de la communauté
Relevés « Ça charge / Ne charge pas » (borne, résultat, date, modèle de voiture, puissance facultative) et états de borne (panne, occupée, file, libre, panne réglée) : envoyés anonymement par les utilisateurs, sans nom, plaque, trajet ni position GPS de l'utilisateur.
**Licence : ODbL 1.0** (décision du projet), avec mention de la source (« relevés de la communauté de l'application Bornes Smart et Zoé »). Les positions et noms de bornes issus d'OpenStreetMap y restent © contributeurs OpenStreetMap.
**Exception à « sans position » :** quand un utilisateur ajoute une nouvelle borne et choisit de la partager (case décochée par défaut), la position de cette borne, donc celle où il se trouvait, est envoyée. Une borne privée n'est jamais envoyée.
Les relevés ne sont pas des garanties : une borne qui a chargé une fois peut refuser ensuite.
Les stickers « vus, non vérifiés » sont des constats visuels non testés ; ils sont affichés à part et ne changent jamais le verdict. Le signalement d'un essai par un tiers n'est plus proposé.

## Pas de disponibilité en temps réel
L'application n'utilise aucun flux de disponibilité d'opérateur. Toute intégration future (par exemple transportdata.be, CC BY 4.0) citera sa source.
