package org.borneneutre.app

/**
 * Aide hors ligne à la demande de changement de raccordement (3×230 V vers 3×400 V + neutre).
 * L'application ne remplit rien et n'envoie rien : elle liste les pièces demandées, propose un texte à copier
 * et un lien vers la page du gestionnaire (seulement quand l'adresse a été vérifiée).
 * Sources lues le 18/11/2026 (voir claude/gestionnaires-belges-modification-raccordement-2026-11-18.md et
 * claude/resa-dce20-lecture-2026-11-18.md) ; l'écran du formulaire Sibelga est rapporté d'après des captures.
 */
data class Gestionnaire(
    val id: String,
    val region: String,
    val nom: String,
    val demarche: String,
    val pieces: List<String>,
    val aSavoir: List<String>,
    val source: String,
    val url: String = ""
)

object Raccord {
    val REGIONS = listOf("Bruxelles", "Flandre", "Wallonie")

    val TOUS: List<Gestionnaire> = listOf(
        Gestionnaire(
            "sibelga", "Bruxelles", "Sibelga",
            "Formulaire en ligne « Modifier la puissance » sur my.sibelga.be (connexion requise ; 9 étapes).",
            listOf(
                "Code EAN : 18 chiffres commençant par 5414489, sur la facture du fournisseur (attention aux autres numéros de la facture)",
                "Un mandat si vous n'êtes pas titulaire du compteur (locataire, électricien…)",
                "Le certificat de conformité de l'installation (RGIE)",
                "Deux photos de l'installation"
            ),
            listOf(
                "Écrivez votre souhait « 3×400 V + neutre » dans le champ prévu pour la raison ou les remarques : rien ne dit que le formulaire propose ce choix.",
                "Les étapes 2 à 9 n'ont pas été relues : l'ordre et les noms peuvent différer."
            ),
            "Écrans de préparation du formulaire (rapporté, 18/11/2026)",
            "https://my.sibelga.be"
        ),
        Gestionnaire(
            "fluvius", "Flandre", "Fluvius",
            "Demande de modification de puissance par Mijn Fluvius. Pour une question générale : formulaire de contact (« Ik heb een vraag »).",
            listOf(
                "Code EAN",
                "Photos détaillées du câble de raccordement",
                "Photos d'ensemble du mur ou du coffret des compteurs",
                "Plan au sol indiquant l'emplacement des compteurs (pour une augmentation)"
            ),
            listOf(
                "Fluvius propose un outil en ligne, la « Spanningschecker », pour savoir si le 400 V est disponible à votre adresse.",
                "Un supplément « 400 V sur demande » existait en 2025 (environ 173 € TVAC, à vérifier)."
            ),
            "Pages Fluvius lues le 18/11/2026",
            "https://www.fluvius.be/nl/contact/contactformulier"
        ),
        Gestionnaire(
            "ores", "Wallonie", "ORES (hors Liège)",
            "Formulaire en ligne « modifier le raccordement » ou formulaire PDF (POW_DOF_E_3).",
            listOf(
                "Type d'alimentation souhaité : monophasé 230 V, triphasé 230 V ou tétraphasé 400 V",
                "Photo du coffret (obligatoire)"
            ),
            listOf("Le triphasé et le tétraphasé demandent l'accord du gestionnaire."),
            "Formulaire PDF ORES lu le 18/11/2026"
        ),
        Gestionnaire(
            "resa", "Wallonie", "RESA (Liège)",
            "Formulaire en ligne « laissez-vous guider » ou formulaire PDF DCE20 (juillet 2026).",
            listOf(
                "Code de travail B (renforcement de puissance ou changement de tension) et choix « triphasé 3N400V »",
                "Borne de recharge à indiquer dans « équipement particulier », avec sa puissance en kW",
                "Plan d'implantation du bâtiment et plan de l'emplacement souhaité du compteur",
                "Photos du réseau électrique devant le logement, et photo de face de chaque compteur"
            ),
            listOf(
                "Le formulaire prévoit un repli en 3×230 V si le réseau n'est pas disponible en 400 V ; dans ce cas, les travaux sur l'installation privée sont à votre charge.",
                "Aucun prix ni délai dans le formulaire : l'offre les donne après étude."
            ),
            "Formulaire DCE20 lu le 18/11/2026 (pages 2 à 4 ; le formulaire en ligne n'a pas été lu)"
        ),
        Gestionnaire(
            "aiesh", "Wallonie", "AIESH (sud du Hainaut)",
            "Formulaire en ligne « Formulaire de modification » (raccordements existants). Tél. 060/45.91.60 ; clients@aiesh.be.",
            listOf("Adresse et code EAN", "Type de modification : choisissez « autre » et précisez"),
            listOf("Le formulaire lu ne parle pas de 230 V ou 400 V : écrivez votre souhait dans la précision."),
            "Page AIESH lue le 18/11/2026"
        ),
        Gestionnaire(
            "aieg", "Wallonie", "AIEG (Andenne)",
            "Page « Je modifie mon raccordement » : formulaire en ligne, visite technique, devis, paiement. Tél. 085/274 900.",
            listOf("Code EAN (obligatoire)"),
            listOf(
                "Procédure en 8 étapes ; compteur placé sous 30 jours après paiement.",
                "Une réception de l'installation par un organisme agréé peut être demandée.",
                "La page lue ne parle pas de 230 V ou 400 V : écrivez votre souhait dans le formulaire."
            ),
            "Page AIEG lue le 18/11/2026"
        ),
        Gestionnaire(
            "rew", "Wallonie", "REW (Wavre)",
            "Pas de page de modification de raccordement retrouvée : contactez le gestionnaire (coordonnées sur sa page d'accueil).",
            listOf("Code EAN"),
            listOf("Pour une installation photovoltaïque, un changement de raccordement doit être demandé avant la déclaration."),
            "Recherche du 18/11/2026 (non lu en détail)"
        )
    )

    fun deRegion(region: String): List<Gestionnaire> = TOUS.filter { it.region == region }

    /** Texte à copier et adapter ; aucune donnée personnelle n'est insérée par l'application. */
    fun modele(g: Gestionnaire): String =
        "Bonjour,\n\n" +
            "Je souhaite connaître le type d'alimentation de mon raccordement électrique (code EAN : …, adresse : …) : 3×230 V ou 3×400 V + neutre ? Je ne le sais pas avec certitude.\n" +
            "Si l'alimentation est en 3×230 V, serait-il possible, à cette adresse, de passer à une alimentation en 3×400 V + neutre (triphasé), " +
            "afin de recharger un véhicule électrique dont le chargeur embarqué semble exiger un neutre ?\n\n" +
            "Pouvez-vous m'indiquer le type d'alimentation actuel, la procédure à suivre auprès de ${g.nom.substringBefore(" (")}, les travaux nécessaires, le coût et le délai ?\n\n" +
            "Je joins les photos et documents demandés.\n\nCordialement,\n…"
}
