# Installer le serveur partagé (Supabase)

L'application fonctionne sans serveur (relevés locaux). Pour héberger votre propre base partagée, créez un projet Supabase gratuit, puis collez dans **SQL Editor** les scripts, **dans cet ordre** (chacun peut être relancé sans danger) :

1. `SUPABASE-INSTALLATION-COMPLETE.sql` : table des relevés, limites anti-abus, liste de voitures ouverte.
2. `SUPABASE-MAJ-1.0.2-REMOTE.sql` : colonne « essai noté après coup » (`remote`).
3. `SUPABASE-MAJ-1.0.3.sql` : états de borne (panne, occupée…).
4. `SUPABASE-MAJ-1.0.4.sql` : fiches de borne (points, prises, puissance).
5. `SUPABASE-MAJ-1.0.5.sql` : téléphone d'assistance sur la fiche.

Les fichiers `supabase.sql` et `supabase-v2.sql` sont les premières versions, conservées pour l'historique ; ne les utilisez pas pour une nouvelle installation.
Adresse du projet et clé publique (anon) : constantes en tête de `app/src/main/java/org/borneneutre/app/Sync.kt` ; il faut recompiler pour changer de serveur.
Aucune donnée personnelle : l'identifiant de téléphone est aléatoire et n'est pas lisible par les autres utilisateurs.
