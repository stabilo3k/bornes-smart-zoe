package org.borneneutre.app

/** Bornes « à essayer » : les bornes de la concession de Bruxelles Mobilité (Région bruxelloise, état « installée, disponible ») classées « 400V - 22kW ».
 *  Source : couche WFS charging_stations_concession (data.mobility.brussels, CC BY), lue le 18/11/2026.
 *  C'est une classification de conception du concessionnaire, pas une mesure : le marquage dit « à essayer », jamais « compatible ». */
object Essai400 {
    data class Borne(val ref: String, val adresse: String, val lat: Double, val lon: Double)

    val BORNES = listOf(
        Borne("PP003220", "Avenue du Bourgmestre Jean Neybergh 18", 50.8901, 4.3110),
        Borne("PP003222", "Rue Gaston Biernaux 18", 50.8798, 4.3208),
        Borne("PP003221", "Rue Tiebackx 3", 50.8866, 4.3184),
        Borne("PP003219", "Avenue Joseph de Heyn (numéro douteux dans la source)", 50.8928, 4.3216),
        Borne("PP003223", "Avenue de Jette 166", 50.8693, 4.3258),
        Borne("PP003285", "Avenue de Laeken 61", 50.8705, 4.3221),
        Borne("PP003279", "Avenue de Jette 330", 50.8737, 4.3228),
        // Autres bornes de la concession classées « 400V - 22kW », état « INSTALLED - AVAILABLE » (couche lue le 20/11/2026 ; adresse non reprise).
        Borne("PP003265", "", 50.83460, 4.30710),
        Borne("PP003252", "", 50.83860, 4.32910),
        Borne("PP003275", "", 50.82820, 4.30600),
        Borne("PP003286", "", 50.84720, 4.35610),
        Borne("PP003256", "", 50.85220, 4.34810),
        Borne("PP003300", "", 50.84350, 4.37050),
        Borne("PP003278", "", 50.84510, 4.37540),
        Borne("PP003289", "", 50.84600, 4.38370),
        Borne("PP003254", "", 50.83320, 4.37590),
        Borne("PP003291", "", 50.82530, 4.38280),
        Borne("PP003292", "", 50.81760, 4.35490),
        Borne("PP003213", "", 50.83890, 4.39940),
        Borne("PP003212", "", 50.83780, 4.38570),
        Borne("PP003264", "", 50.81820, 4.42940),
        Borne("PP003266", "", 50.81850, 4.40700),
        Borne("PP003271", "", 50.82240, 4.40390),
        Borne("PP003237", "", 50.86720, 4.37380),
        Borne("PP003234", "", 50.85170, 4.38780),
        Borne("PP003259", "", 50.83350, 4.33710),
        Borne("PP003258", "", 50.82950, 4.35510),
        Borne("PP003257", "", 50.82620, 4.34400),
        Borne("PP003296", "", 50.85500, 4.31230),
        Borne("PP003229", "", 50.86050, 4.34630),
        Borne("PP003253", "", 50.78380, 4.37930),
        Borne("PP003299", "", 50.80120, 4.34270),
        Borne("PP003203", "", 50.87100, 4.40730),
        Borne("PP003204", "", 50.86310, 4.42110),
        Borne("PP003206", "", 50.85650, 4.41370),
        Borne("PP003207", "", 50.86620, 4.40380),
        Borne("PP003211", "", 50.87530, 4.31390),
        Borne("PP003209", "", 50.87520, 4.30020),
        Borne("PP003210", "", 50.87490, 4.30910),
        Borne("PP003208", "", 50.87660, 4.30160),
        Borne("PP003272", "", 50.82150, 4.28440),
        Borne("PP003263", "", 50.83250, 4.34290),
        Borne("PP003273", "", 50.83000, 4.45810),
        Borne("PP003216", "", 50.86790, 4.39070),
        Borne("PP003217", "", 50.85870, 4.37880),
        Borne("PP003235", "", 50.85470, 4.39780),
        Borne("PP003233", "", 50.82770, 4.35920),
        Borne("PP003236", "", 50.82290, 4.32580),
        Borne("PP003230", "", 50.82410, 4.32790),
        Borne("PP003267", "", 50.80630, 4.31740),
        Borne("PP003261", "", 50.81480, 4.40410),
        Borne("PP003268", "", 50.80180, 4.42290),
        Borne("PP003280", "", 50.81320, 4.39180),
        Borne("PP003274", "", 50.83870, 4.46630),
        Borne("PP003270", "", 50.83650, 4.41540),
        Borne("PP003293", "", 50.85180, 4.30300),
        Borne("PP003225", "", 50.84030, 4.40250),
        Borne("PP003226", "", 50.84820, 4.42960),
        Borne("PP003260", "", 50.82120, 4.42840),
        Borne("PP003277", "", 50.83540, 4.35250),
        Borne("PP003281", "", 50.83970, 4.35940),
        Borne("PP003255", "", 50.87080, 4.35270),
        Borne("PP003247", "", 50.79050, 4.37460),
        Borne("PP003262", "", 50.78640, 4.42500),
        Borne("PP003215", "", 50.87240, 4.29510),
        Borne("PP003214", "", 50.81640, 4.35160),
        Borne("PP003242", "", 50.84950, 4.46030),
        Borne("PP003295", "", 50.81450, 4.37920),
        Borne("PP003202", "", 50.82950, 4.38700),
        Borne("PP003224", "", 50.89690, 4.38790),
        Borne("PP003294", "", 50.85000, 4.35710),
        Borne("PP003228", "", 50.86540, 4.38050),
        Borne("PP003218", "", 50.86490, 4.39080),
        Borne("PP003283", "", 50.79620, 4.32780),
        Borne("PP003297", "", 50.78860, 4.33300),
        Borne("PP003276", "", 50.88210, 4.40730),
        Borne("PP003227", "", 50.88780, 4.42390),
        Borne("PP003288", "", 50.81860, 4.37150),
        Borne("PP003298", "", 50.80990, 4.36920),
        Borne("PP003282", "", 50.89890, 4.34680),
        Borne("PP003269", "", 50.85110, 4.39680)
    )

    /** Rayon d'appariement (m) : la position de la source est précise à 30 m environ. */
    const val RAYON_M = 40.0

    fun near(lat: Double, lon: Double): Borne? = BORNES.firstOrNull {
        val dy = (lat - it.lat) * 111_000.0
        val dx = (lon - it.lon) * 111_000.0 * Math.cos(Math.toRadians(lat))
        Math.hypot(dx, dy) <= RAYON_M
    }

    /** Tout essai enregistré est concluant, sauf un refus hors verdict (badge, borne éteinte, carte) qui ne dit rien de la compatibilité. */
    fun concluant(r: Releve): Boolean = !r.horsVerdict()

    /** L'anneau « à essayer » reste affiché tant qu'aucun essai concluant n'existe pour la voiture de l'utilisateur. */
    fun aEssayer(lat: Double, lon: Double, relevesMaVoiture: List<Releve>): Boolean =
        near(lat, lon) != null && relevesMaVoiture.none { concluant(it) }
}

/** Passe Fable n° 7 (C2) : les anneaux violet et gris ne valent que pour les bornes du réseau TotalEnergies / charge.brussels (ou d'exploitant non renseigné)
 *  et jamais pour le courant continu : une borne voisine d'un autre exploitant (Tesla, EnergyVision, Blue Corner…) n'en reçoit pas. */
private val OPERATEURS_CONCESSION = Regex("total|pitpoint|charge\\.?brussels", RegexOption.IGNORE_CASE)
fun Station.dansLaConcession(): Boolean = current != "DC" && (operator.isBlank() || OPERATEURS_CONCESSION.containsMatchIn(operator))
fun Station.anneau400(forCar: List<Releve>): Boolean = dansLaConcession() && Essai400.aEssayer(lat, lon, forCar)
fun Station.anneau230(): Boolean = dansLaConcession() && Te230.near(lat, lon) != null
