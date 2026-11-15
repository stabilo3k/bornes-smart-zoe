# BorneNeutre

Application Android personnelle qui cartographie les bornes de recharge belges où une Smart EQ (et d'autres voitures qui exigent un neutre, comme la Renault Zoé) charge réellement.

Contexte : une partie du réseau belge est en 3×230 V sans neutre. Certaines voitures refusent alors de charger. Aucune base publique n'indique le neutre ; l'information vient des essais des conducteurs.

État : version 1.0.

Version 1.0-rc1 : deux gros boutons, « Ça charge » / « Ne charge pas ». L'app prend la position GPS (compteur visible), retrouve la borne (bornes connues, puis OpenStreetMap via Overpass, sinon nouvelle borne), demande confirmation et enregistre ; annulation possible 8 secondes avant l'envoi. Voiture par défaut : Smart EQ fortwo ; liste fermée : Smart EQ fortwo/forfour, Renault Zoé, Renault Twingo électrique. Carte colorée, zones estimées (expérimentales), relevés partagés anonymes (Supabase : `docs/supabase.sql`, `docs/supabase-v2.sql`), FAQ, écran « À propos ».

Licence : GPL-3.0-or-later (fichier `LICENSE`). Compilation : `gradle assembleRelease` (Gradle 8+, pas de wrapper dans le dépôt) ; la signature de publication se passe par `-PBN_KEYSTORE=... -PBN_PROPS=...` (clé hors dépôt).

Sources de données : © contributeurs OpenStreetMap (ODbL, bornes via Overpass), OpenFreeMap, Bruxelles Mobilité (CC0, CC BY).

Aucune donnée personnelle dans ce dépôt : ni plaque, ni numéro de châssis, ni position de l'utilisateur.
