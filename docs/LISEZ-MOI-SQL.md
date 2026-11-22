# Scripts SQL du serveur (Supabase)

À exécuter par le mainteneur dans l'éditeur SQL de Supabase. L'application n'en exécute aucun. Sauvegardez d'abord (workflow `supabase-export`).

Installation neuve, dans l'ordre : `supabase.sql`, `supabase-v2.sql`, puis les mises à jour ci-dessous.

| Fichier | Rôle |
|---|---|
| `supabase.sql` | Tables et règles de base (relevés) |
| `supabase-v2.sql` | Limites d'envoi par téléphone et par heure |
| `SUPABASE-MAJ-1.0.3.sql`, `-1.0.4.sql`, `-1.0.5.sql` | Mises à jour des versions 1.0.x |
| `SUPABASE-MAJ-1.3-pastilles.sql` | Table des pastilles vues ; lecture sans la colonne `device` ; fonction de limite « security definer » (correctif du 20/11/2026) |
| `SUPABASE-PURGE.sql` | Purge : états 90 jours ; identifiant de téléphone effacé après 12 mois ; relevés, fiches et pastilles supprimés après 3 ans. À lancer une fois par mois (ou planifier avec pg_cron) |

La colonne `device` (identifiant aléatoire du téléphone) n'est jamais lisible publiquement.
