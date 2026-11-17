# Bornes Smart et Zoé

Application Android gratuite, libre et sans publicité : les conducteurs de **Smart EQ, Renault Zoé** et voitures voisines signalent en deux touches si une borne belge a chargé ou non leur voiture (certaines refusent les réseaux 3×230 V sans neutre).

## Installer l'application (Android)

1. Sur ton téléphone Android, ouvre la **[dernière version](https://github.com/stabilo3k/bornes-smart-zoe/releases/latest)**.
2. Descends jusqu'à « Assets » et touche le fichier **`BorneNeutre-….apk`** pour le télécharger.
3. Ouvre le fichier téléchargé. Android demande d'autoriser l'installation depuis ce navigateur : accepte (« Installer des applications inconnues »), puis touche « Installer ».
4. Au premier lancement, choisis ta voiture. Les relevés sont anonymes.

*English: open the [latest release](https://github.com/stabilo3k/bornes-smart-zoe/releases/latest) on your Android phone, download the `.apk` under "Assets", open it and allow installation from this browser. A listing on F-Droid has been requested.*

---

Contexte : une partie du réseau belge est en 3×230 V sans neutre. Certaines voitures refusent alors de charger. Aucune base publique n'indique le neutre ; l'information vient des essais des conducteurs.

État : version 1.0.6 (fiches de borne, pannes et états, urgence batterie, liste de bornes de Belgique intégrée, bornes de Flandre en données ouvertes).

Version 1.0-rc1 : deux gros boutons, « Ça charge » / « Ne charge pas ». L'app prend la position GPS (compteur visible), retrouve la borne (bornes connues, puis OpenStreetMap via Overpass, sinon nouvelle borne), demande confirmation et enregistre ; annulation possible 8 secondes avant l'envoi. Voiture par défaut : Smart EQ fortwo ; liste fermée : Smart EQ fortwo/forfour, Renault Zoé, Renault Twingo électrique. Carte colorée, zones estimées (expérimentales), relevés partagés anonymes (Supabase : `docs/supabase.sql`, `docs/supabase-v2.sql`), FAQ, écran « À propos ».

Licence : GPL-3.0-or-later (fichier `LICENSE`). Compilation : `gradle assembleRelease` (Gradle 8+, pas de wrapper dans le dépôt) ; la signature de publication se passe par `-PBN_KEYSTORE=... -PBN_PROPS=...` (clé hors dépôt).

Sources de données : © contributeurs OpenStreetMap (ODbL), OpenFreeMap, Bruxelles Mobilité (CC0, CC BY), Departement Mobiliteit en Openbare Werken / Eco-Movement (Open Data Vlaanderen). Détails : `DATA-LICENCE.md`.

Aucune donnée personnelle dans ce dépôt : ni plaque, ni numéro de châssis, ni position de l'utilisateur.
