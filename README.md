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

État : version 1.1.0. Deux gros boutons, « Ça charge » / « Ne charge pas » ; l'app prend la position GPS, retrouve la borne (listes intégrées de Belgique et de Bruxelles, données ouvertes flamandes, OpenStreetMap), demande confirmation et enregistre, avec annulation possible pendant 8 secondes. Voitures (liste fermée de sept modèles) : Smart EQ fortwo/forfour, Renault Zoé, Twingo électrique, Kangoo E-Tech, Nissan Townstar, Mercedes eCitan. Carte colorée, fiches de borne (adresse, source, points, prises, téléphone d'assistance), pannes et états, urgence batterie, FAQ sourcée, écran « À propos », mise à jour mensuelle des listes de bornes (dossier `data/`). Relevés partagés anonymes sur un serveur Supabase : installation décrite dans `docs/LISEZ-MOI-SQL.md`. Si le serveur s'arrête, l'application continue de fonctionner hors ligne.

Licence : GPL-3.0-or-later (fichier `LICENSE`). Compilation : `gradle assembleRelease` (Gradle 8+, pas de wrapper dans le dépôt) ; la signature de publication se passe par `-PBN_KEYSTORE=... -PBN_PROPS=...` (clé hors dépôt).

Sources de données : © contributeurs OpenStreetMap (ODbL), OpenFreeMap, Bruxelles Mobilité (CC0, CC BY), Departement Mobiliteit en Openbare Werken / Eco-Movement (Open Data Vlaanderen). Détails : `DATA-LICENCE.md`.

Aucune donnée personnelle dans ce dépôt : ni plaque, ni numéro de châssis, ni position de l'utilisateur.
