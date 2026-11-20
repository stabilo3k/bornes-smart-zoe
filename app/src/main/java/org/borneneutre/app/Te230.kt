package org.borneneutre.app

/** Bornes de la liste « 230 V » de TotalEnergies (Charging Belgium), transmise par leur support le 18/11/2026 (ticket #38732),
 *  « informations issues de nos données en date du 16/11 » ; publication autorisée par eux à cette condition.
 *  La liste ne contient que des adresses : chaque adresse a été placée (géocodage OpenStreetMap, ODbL) puis rapprochée de l'emplacement
 *  TotalEnergies « AC_1_PHASE » le plus proche (jeu Bruxelles Mobilité, CC BY, à 66 m au plus). 68 adresses sur 75 sont retenues ;
 *  7 ne sont pas placées (4 introuvables, 3 sans emplacement TotalEnergies cohérent).
 *  TotalEnergies ne distingue pas le 230 V monophasé du 3×230 V : la liste ne dit pas que la voiture ne charge pas. */
object Te230 {
    data class P(val adresse: String, val lat: Double, val lon: Double)

    const val SOURCE = "TotalEnergies / Charging Belgium, données du 16/11/2026"
    const val RAYON_M = 40.0

    val BORNES = listOf(
        P("Avenue Bourgmestre Jean Herinckx 23", 50.81040, 4.34940),
        P("Avenue Emile Vandervelde 117", 50.84770, 4.44900),
        P("Avenue Everard 9", 50.81630, 4.33760),
        P("Avenue Grandchamp 314", 50.84080, 4.45850),
        P("Avenue Lloyd George 6", 50.81500, 4.37370),
        P("Avenue Louis Bertrand 10", 50.86420, 4.37410),
        P("Avenue Louis Vercauteren 17", 50.81750, 4.43320),
        P("Avenue Louise 441", 50.81830, 4.37080),
        P("Avenue Nestor Plissart 6", 50.83200, 4.40720),
        P("Avenue Notre-Dame de Lourdes 32", 50.87340, 4.33640),
        P("Avenue Paul Hymans 110", 50.84700, 4.43310),
        P("Avenue Paul Hymans 71", 50.84620, 4.42800),
        P("Avenue d'Auderghem 247", 50.83300, 4.38880),
        P("Avenue de Broqueville 2", 50.83790, 4.40900),
        P("Avenue de l'Indépendance Belge 59", 50.86330, 4.32020),
        P("Avenue des Cattleyas 19", 50.82640, 4.41030),
        P("Avenue des Charançons 1", 50.79880, 4.40120),
        P("Avenue le Marinel 18", 50.82980, 4.39820),
        P("Boulevard Brand Whitlock 83", 50.84160, 4.40640),
        P("Boulevard du Neuvième de Ligne 35", 50.85690, 4.34520),
        P("Chaussée de Bruxelles 112", 50.81300, 4.32240),
        P("Chaussée de Charleroi 279", 50.82310, 4.35370),
        P("Chaussée de Mons 1224", 50.81830, 4.29220),
        P("Chaussée de Mons 901", 50.82730, 4.30100),
        P("Chaussée de Wavre 1520A", 50.81770, 4.42040),
        P("De Moerkerkestraat 22", 50.86840, 4.36890),
        P("De Ribaucourtstraat 185", 50.86370, 4.34470),
        P("Frédéric Pelletierstraat 111", 50.84600, 4.40310),
        P("Grootveldlaan 208", 50.83760, 4.45380),
        P("Hertogendal 352", 50.82100, 4.40960),
        P("Koning Albertlaan 140", 50.89370, 4.37560),
        P("Louizalaan 98", 50.83150, 4.35990),
        P("Nerviërslaan 109", 50.83840, 4.39120),
        P("Rouppeplein 17", 50.84250, 4.34610),
        P("Rue Adolphe Willemyns 260", 50.83970, 4.29100),
        P("Rue Alfred Dubois 49", 50.85240, 4.31300),
        P("Rue Baron Dhanis 35", 50.82920, 4.39690),
        P("Rue Chant d'Oiseaux 103", 50.81340, 4.28500),
        P("Rue Delaunoy 63", 50.85140, 4.33310),
        P("Rue Docteur Elie Lambotte 228", 50.87380, 4.39210),
        P("Rue Emile Claus 2", 50.81620, 4.37100),
        P("Rue Franklin 39", 50.84480, 4.38550),
        P("Rue François Desmedt 97", 50.84310, 4.46430),
        P("Rue Gustave Van Huynegem 4", 50.87870, 4.32860),
        P("Rue Général Leman 168", 50.83690, 4.38840),
        P("Rue Léon Scampart 9", 50.83480, 4.39640),
        P("Rue Léon de Lantsheere 42", 50.84350, 4.39970),
        P("Rue Léopold Peret 10", 50.88520, 4.33570),
        P("Rue Mareyde 1", 50.83220, 4.44600),
        P("Rue Meyerbeer 174", 50.81430, 4.33970),
        P("Rue Molensteen 36", 50.78440, 4.32630),
        P("Rue Solleveld 2", 50.84540, 4.43040),
        P("Rue Stéphanie 174", 50.87730, 4.34910),
        P("Rue Victor Oudart 2", 50.84890, 4.39220),
        P("Rue de Birmingham 41", 50.84810, 4.32700),
        P("Rue de Theux 60", 50.83280, 4.38230),
        P("Rue de Vrière 16", 50.88220, 4.35320),
        P("Rue de la Brasserie 69", 50.82850, 4.37630),
        P("Rue de la Cambre 16", 50.83940, 4.42990),
        P("Rue des Horticulteurs 147", 50.88220, 4.34820),
        P("Rue des Taxandres 20", 50.83630, 4.40230),
        P("Rue des Vétérinaires 35", 50.83510, 4.32560),
        P("Rue du Facteur 13", 50.85580, 4.33840),
        P("Rue du Gruyer 8", 50.80980, 4.40490),
        P("Venezuelalaan 2", 50.80140, 4.39060),
        P("Zespenningenstraat 59", 50.84750, 4.34380),
        P("Zevenbunderslaan 85", 50.81300, 4.33500),
        P("Zwartkeeltjeslaan 147A", 50.82240, 4.42020),    )

    fun near(lat: Double, lon: Double): P? = BORNES.firstOrNull {
        val dy = (lat - it.lat) * 111_000.0
        val dx = (lon - it.lon) * 111_000.0 * Math.cos(Math.toRadians(lat))
        Math.hypot(dx, dy) <= RAYON_M
    }
}
