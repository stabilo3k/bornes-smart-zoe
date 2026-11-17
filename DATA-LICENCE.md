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

## Fond de carte
OpenFreeMap / OpenMapTiles, données © contributeurs OpenStreetMap.

## Relevés et signalements de la communauté
Relevés « Ça charge / Ne charge pas » (borne, résultat, date, modèle de voiture, puissance facultative) et états de borne (panne, occupée, file, libre, déclaré par un tiers) : envoyés anonymement par les utilisateurs, sans nom, plaque, trajet ni position GPS de l'utilisateur.
Les relevés ne sont pas des garanties : une borne qui a chargé une fois peut refuser ensuite.
Les signalements « déclaré, non vérifié » sont des témoignages non testés ; ils sont affichés à part et ne changent jamais le verdict.

## Pas de disponibilité en temps réel
L'application n'utilise aucun flux de disponibilité d'opérateur. Toute intégration future (par exemple transportdata.be, CC BY 4.0) citera sa source.
