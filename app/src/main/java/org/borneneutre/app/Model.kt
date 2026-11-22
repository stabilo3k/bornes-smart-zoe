package org.borneneutre.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Result { OK, REFUS, LIMITE }

data class Station(
    val id: String,
    val name: String,
    val operator: String,
    val lat: Double,
    val lon: Double,
    val current: String, // AC, DC ou ?
    val note: String,
    val kind: String, // releve, candidat, osm, prive, user
    val params: String = "" // paramètres techniques de la borne (étiquettes OSM, JSON), gardés en interne, jamais affichés
)

data class Releve(
    val id: String,
    val stationId: String,
    val ts: String,
    val result: Result,
    val note: String,
    val seed: Boolean,
    val car: String = "",
    val remote: Boolean = false,
    val kw: Double? = null, // puissance maximale observée (kW), facultative, déclarée par l'utilisateur ; peut varier (charge intelligente)
    val motif: String = "", // motif du refus, facultatif : "voyant", "eteinte", "badge", "carte" ou ""
    val demarrage: String = "" // démarrage de la charge, facultatif : voir DEMARRAGES
)

/** Comment la charge a été démarrée (facultatif, choix fermés). Partie des relevés ; jamais un verdict. */
val DEMARRAGES = listOf("carte" to "Carte ou badge", "appli" to "Appli du fournisseur", "qr" to "QR code", "cb" to "Carte bancaire", "autre" to "Autre", "nsp" to "Je ne sais pas")
fun demarrageLabel(code: String): String? = DEMARRAGES.firstOrNull { it.first == code }?.second

const val DEFAULT_CAR = "Smart EQ fortwo"

/** Motif d'un refus, en une touche facultative (choix fermés). Le code part au serveur, le libellé reste sur le téléphone. */
val MOTIFS = listOf("voyant" to "Voyant orange puis coupure", "eteinte" to "Borne éteinte ou sans réponse", "badge" to "Badge ou authentification refusé", "carte" to "Carte ou appli refusée (la borne n'a pas démarré)")

/** Un refus dont le motif ne dit rien du réseau (badge, borne éteinte, carte ou appli refusée) n'est PAS un verdict de compatibilité. */
fun Releve.horsVerdict(): Boolean = result == Result.REFUS && motif in setOf("badge", "eteinte", "carte")
fun motifLabel(code: String): String? = MOTIFS.firstOrNull { it.first == code }?.second

/** Pastille de charge.brussels vue sur la borne (choix fermés). */
val PASTILLES = listOf("verte" to "Vert (22 kW, 3×400 V + N)", "blanche" to "Blanc (7,2 kW, 1×230 V)",
    "bleue" to "Bleu EnergyDrive (22 kW, 3×400 V + N)", "autre" to "Autre sticker", "aucune" to "Aucun sticker")
fun pastilleLabel(code: String): String? = PASTILLES.firstOrNull { it.first == code }?.second

/** Liste fermée : voitures dont le chargeur 22 kW est connu pour refuser certains réseaux 3×230 V (Brugel 2020 ; Elbilgrossisten). */
val CARS = listOf("Smart EQ fortwo", "Smart EQ forfour", "Renault Zoé", "Renault Twingo électrique", "Renault Kangoo E-Tech", "Nissan Townstar EV", "Mercedes eCitan")

/** Voitures dont une version accepte la charge rapide en courant continu (CCS) : les bornes DC leur restent utiles en secours. La smart EQ n'en a pas. */
fun carAcceptsDc(car: String): Boolean = car in setOf("Renault Zoé", "Renault Kangoo E-Tech", "Nissan Townstar EV")

private fun norm(x: String): String =
    java.text.Normalizer.normalize(x.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Retourne le nom canonique de la voiture, ou null si elle n'est pas dans la liste fermée. Un champ vide (anciens relevés) = Smart EQ fortwo. */
fun canonicalCar(car: String): String? {
    if (car.isBlank()) return DEFAULT_CAR
    val n = norm(car)
    return CARS.firstOrNull { norm(it) == n }
}

fun isKnownCar(car: String) = canonicalCar(car) != null

object Geo {
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}

/** Couleurs du verdict (rapport de contraste ≥ 4,5:1 sur fond blanc). Le symbole (✓ ✕ ±) double toujours la couleur : daltonisme. */
const val COLOR_OK = "#15803d"
const val COLOR_NO = "#d62728"
const val COLOR_MIX = "#b45309"
const val COLOR_NONE = "#6b7280"

/** Symbole du marqueur : "ok", "no", "mix" ou "" (pas essayée). */
fun statusSymbol(color: String): String = when (color) { COLOR_OK -> "ok"; COLOR_NO -> "no"; COLOR_MIX -> "mix"; else -> "" }

/** Statut simple d'une borne pour les voitures de la liste : vert / rouge / orange / gris. */
data class SimpleStatus(val label: String, val color: String, val n: Int, val tried: Boolean)

fun simpleStatus(tous: List<Releve>): SimpleStatus {
    val releves = tous.filterNot { it.horsVerdict() }
    val ok = releves.count { it.result == Result.OK }
    val ko = releves.count { it.result == Result.REFUS }
    val n = releves.size
    val s = if (n > 1) "s" else ""
    if (n == 0) return SimpleStatus("Pas encore essayée", COLOR_NONE, 0, false)
    val share = ok.toDouble() / n
    return when {
        ko == 0 && ok == n -> SimpleStatus("Ça charge ($n relevé$s)", COLOR_OK, n, true)
        ok == 0 && ko == n -> SimpleStatus("Ne charge pas ($n relevé$s)", COLOR_NO, n, true)
        n >= 5 && share >= 0.8 -> SimpleStatus("Ça charge en général ($ok sur $n, $ko refus)", COLOR_OK, n, true)
        n >= 5 && share <= 0.2 -> SimpleStatus("Ne charge pas en général ($ko refus sur $n)", COLOR_NO, n, true)
        else -> SimpleStatus("Essais opposés : $ok réussi${if (ok > 1) "s" else ""}, $ko refus", COLOR_MIX, n, true)
    }
}

/** Verdict d'un modèle de voiture sur une borne. */
data class ModelVerdict(val car: String, val status: SimpleStatus)

/** Verdicts par modèle : « ma voiture » d'abord (même sans essai), puis les autres modèles ayant au moins un relevé, du plus documenté au moins documenté. */
fun verdictsByModel(releves: List<Releve>, myCar: String): List<ModelVerdict> {
    val by = releves.groupBy { canonicalCar(it.car) ?: DEFAULT_CAR }
    val mine = ModelVerdict(myCar, simpleStatus(by[myCar] ?: emptyList()))
    val others = by.filterKeys { it != myCar }.map { (c, l) -> ModelVerdict(c, simpleStatus(l)) }.sortedWith(compareByDescending<ModelVerdict> { it.status.n }.thenBy { it.car })
    return listOf(mine) + others
}

/** Écriture atomique : fichier temporaire puis renommage, pour qu'une coupure ne laisse jamais un fichier tronqué. */
fun File.writeTextAtomic(text: String) {
    val tmp = File(parentFile, name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) { writeText(text); tmp.delete() }
}

object Store {
    private const val FILE = "user_data.json"

    fun loadSeedStations(ctx: Context): List<Station> =
        parseStations(JSONArray(ctx.assets.open("stations.json").bufferedReader().readText()))

    /** Liste des bornes OpenStreetMap de Belgique embarquée (hors ligne ; © contributeurs OpenStreetMap, ODbL) ; vide si le fichier est illisible. */
    fun loadEmbeddedOsm(ctx: Context): List<Station> {
        val local = try { DataUpdate.localFile(ctx, DataUpdate.OSM_FILE)?.let { Overpass.parse(it.readText()) } } catch (e: Exception) { null }
        if (!local.isNullOrEmpty()) return local
        // fichier téléchargé absent, illisible ou vide : repli sur la liste embarquée
        return try { Overpass.parse(ctx.assets.open("bornes-be-osm.json").bufferedReader().readText()) } catch (e: Exception) { emptyList() }
    }

    fun loadSeedReleves(ctx: Context): List<Releve> =
        parseReleves(JSONArray(ctx.assets.open("releves.json").bufferedReader().readText()), true)

    /** A priori par borne issus des données ouvertes de Bruxelles (hypothèses non validées). */
    fun loadPriors(@Suppress("UNUSED_PARAMETER") ctx: Context): Map<String, Double> = emptyMap() // 1.3.2 : les a priori arbitraires ne nourrissent plus aucun libellé (remplacés par Indice)

    fun loadPhones(ctx: Context): Map<String, Int> = try { File(ctx.filesDir, "pastilles-phones.json").takeIf { it.exists() }?.readText()?.let { Pastilles.phonesFromRows(it) } ?: emptyMap() } catch (_: Exception) { emptyMap() }
    fun savePhones(ctx: Context, json: String) { try { File(ctx.filesDir, "pastilles-phones.json").writeTextAtomic(json) } catch (_: Exception) {} }

    fun parsePriors(json: String): Map<String, Double> {
        val a = JSONObject(json).getJSONArray("priors")
        return (0 until a.length()).associate {
            val o = a.getJSONObject(it)
            o.getString("stationId") to o.getDouble("prior")
        }
    }

    fun saveCommunity(ctx: Context, json: String) { try { File(ctx.filesDir, "community.json").writeTextAtomic(json) } catch (_: Exception) {} }
    fun loadCommunity(ctx: Context): String? = try { File(ctx.filesDir, "community.json").takeIf { it.exists() }?.readText() } catch (_: Exception) { null }

    fun loadFiches(ctx: Context): List<Fiche> = try { Fiches.fromJson(File(ctx.filesDir, "fiches.json").takeIf { it.exists() }?.readText() ?: "[]") } catch (e: Exception) { emptyList() }
    fun saveFiches(ctx: Context, l: List<Fiche>) { try { File(ctx.filesDir, "fiches.json").writeTextAtomic(Fiches.toJson(l)) } catch (_: Exception) {} }
    fun loadCommunityFiches(ctx: Context): List<Fiche> = try { Fiches.fromJson(File(ctx.filesDir, "fiches-community.json").takeIf { it.exists() }?.readText() ?: "[]") } catch (e: Exception) { emptyList() }
    fun saveCommunityFiches(ctx: Context, l: List<Fiche>) { try { File(ctx.filesDir, "fiches-community.json").writeTextAtomic(Fiches.toJson(l)) } catch (_: Exception) {} }

    private fun etatsFile(ctx: Context, name: String) = File(ctx.filesDir, name)
    fun loadEtats(ctx: Context): List<Etat> = try { Etats.fromJson(etatsFile(ctx, "etats.json").takeIf { it.exists() }?.readText() ?: "[]") } catch (e: Exception) { emptyList() }
    fun saveEtats(ctx: Context, l: List<Etat>) { try { etatsFile(ctx, "etats.json").writeTextAtomic(Etats.toJson(l)) } catch (_: Exception) {} }
    fun loadCommunityEtats(ctx: Context): List<Etat> = try { Etats.fromJson(etatsFile(ctx, "etats-community.json").takeIf { it.exists() }?.readText() ?: "[]") } catch (e: Exception) { emptyList() }
    fun saveCommunityEtats(ctx: Context, l: List<Etat>) { try { etatsFile(ctx, "etats-community.json").writeTextAtomic(Etats.toJson(l)) } catch (_: Exception) {} }
    fun loadPastilles(ctx: Context): List<Pastille> = try { Pastilles.fromJson(etatsFile(ctx, "pastilles.json").takeIf { it.exists() }?.readText() ?: "[]") } catch (e: Exception) { emptyList() }
    fun savePastilles(ctx: Context, l: List<Pastille>) { try { etatsFile(ctx, "pastilles.json").writeTextAtomic(Pastilles.toJson(l)) } catch (_: Exception) {} }
    fun loadCommunityPastilles(ctx: Context): List<Pastille> = try { Pastilles.fromJson(etatsFile(ctx, "pastilles-community.json").takeIf { it.exists() }?.readText() ?: "[]") } catch (e: Exception) { emptyList() }
    fun saveCommunityPastilles(ctx: Context, l: List<Pastille>) { try { etatsFile(ctx, "pastilles-community.json").writeTextAtomic(Pastilles.toJson(l)) } catch (_: Exception) {} }

    fun loadUser(ctx: Context): Pair<List<Station>, List<Releve>> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return Pair(emptyList(), emptyList())
        return try {
            val o = JSONObject(f.readText())
            Pair(parseStations(o.getJSONArray("stations")), parseReleves(o.getJSONArray("releves"), false))
        } catch (e: Exception) {
            // Fichier illisible : on le garde de côté (jamais écrasé par du vide) pour pouvoir le récupérer.
            try { f.renameTo(File(f.parentFile, FILE + ".corrompu-" + System.currentTimeMillis())) } catch (_: Exception) {}
            Pair(emptyList(), emptyList())
        }
    }

    fun saveUser(ctx: Context, stations: List<Station>, releves: List<Releve>) {
        File(ctx.filesDir, FILE).writeTextAtomic(userJson(stations, releves))
    }

    fun loadCar(ctx: Context): String =
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("car", DEFAULT_CAR)?.let { canonicalCar(it) } ?: DEFAULT_CAR

    fun saveCar(ctx: Context, car: String) {
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("car", canonicalCar(car) ?: DEFAULT_CAR).apply()
    }

    /** Bornes de validité d'une borne importée : Belgique (mêmes bornes que le CHECK du serveur), identifiant court. */
    internal fun validStation(s: Station): Boolean =
        s.lat.isFinite() && s.lon.isFinite() && s.lat in 49.4..51.6 && s.lon in 2.5..6.5 && s.id.isNotBlank() && s.id.length <= 80

    /** Un relevé importé est accepté seulement avec identifiants courts et une date AAAA-MM-JJ valide (sinon le serveur le refuserait pour toujours). */
    internal fun validReleve(r: Releve): Boolean =
        r.id.isNotBlank() && r.id.length <= 80 && r.stationId.isNotBlank() && r.stationId.length <= 80 &&
            runCatching { java.time.LocalDate.parse(r.ts.take(10)) }.isSuccess && r.kw.let { it == null || it.isFinite() }

    /** Fusionne un export reçu (même format que « Exporter ») ; lève une exception si le texte n'est pas valide.
     *  Les éléments invalides (coordonnée non finie ou hors Belgique, date mal formée, identifiant vide ou trop long, doublon dans le fichier) sont écartés. */
    fun importUser(text: String, stations: List<Station>, releves: List<Releve>): Pair<List<Station>, List<Releve>> {
        val o = JSONObject(text.trim())
        val ns = parseStationsSafe(o.optJSONArray("stations") ?: JSONArray()).filter { validStation(it) }.distinctBy { it.id }
        val nr = parseReleves(o.getJSONArray("releves"), false).filter { isKnownCar(it.car) }.map { it.copy(car = canonicalCar(it.car)!!) }
            .filter { validReleve(it) }.distinctBy { it.id }
        val sIds = stations.map { it.id }.toSet()
        val rIds = releves.map { it.id }.toSet()
        return Pair(stations + ns.filter { it.id !in sIds }, releves + nr.filter { it.id !in rIds })
    }

    fun userJson(stations: List<Station>, releves: List<Releve>): String {
        val o = JSONObject()
        o.put("stations", JSONArray().also { a -> stations.forEach { a.put(stationJson(it)) } })
        o.put("releves", JSONArray().also { a -> releves.forEach { a.put(releveJson(it)) } })
        return o.toString(1)
    }

    private fun stationJson(s: Station) = JSONObject()
        .put("id", s.id).put("name", s.name).put("operator", s.operator)
        .put("lat", s.lat).put("lon", s.lon).put("current", s.current)
        .put("note", s.note).put("kind", s.kind).also { if (s.params.isNotBlank()) it.put("params", s.params) }

    private fun releveJson(r: Releve) = JSONObject()
        .put("id", r.id).put("stationId", r.stationId).put("ts", r.ts)
        .put("result", r.result.name).put("note", r.note).put("car", r.car)
        .also { if (r.remote) it.put("remote", true); if (r.kw != null) it.put("kw", r.kw); if (r.motif.isNotBlank()) it.put("motif", r.motif); if (r.demarrage.isNotBlank()) it.put("demarrage", r.demarrage) }

    /** Comme parseStations mais un élément illisible est ignoré au lieu de faire échouer tout l'import. */
    private fun parseStationsSafe(a: JSONArray): List<Station> = (0 until a.length()).mapNotNull {
        runCatching { val o = a.getJSONObject(it)
            Station(o.getString("id"), o.getString("name"), o.optString("operator", ""), o.getDouble("lat"), o.getDouble("lon"),
                o.optString("current", "?"), o.optString("note", ""), o.optString("kind", "user"), o.optString("params", "")) }.getOrNull()
    }

    private fun parseStations(a: JSONArray): List<Station> = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        Station(
            o.getString("id"), o.getString("name"), o.optString("operator", ""),
            o.getDouble("lat"), o.getDouble("lon"), o.optString("current", "?"),
            o.optString("note", ""), o.optString("kind", "user"), o.optString("params", "")
        )
    }

    private fun parseReleves(a: JSONArray, seed: Boolean): List<Releve> = (0 until a.length()).mapNotNull {
        val o = a.optJSONObject(it) ?: return@mapNotNull null
        if (!o.has("id") || !o.has("stationId")) return@mapNotNull null
        val res = runCatching { Result.valueOf(o.getString("result")) }.getOrNull() ?: return@mapNotNull null
        Releve(
            o.getString("id"), o.getString("stationId"), o.optString("ts", ""),
            res,
            o.optString("note", ""), seed, o.optString("car", ""), o.optBoolean("remote", false),
            if (o.has("kw") && !o.isNull("kw")) o.optDouble("kw").takeIf { !it.isNaN() } else null,
            o.optString("motif", "").takeIf { c -> motifLabel(c) != null } ?: "",
            o.optString("demarrage", "").takeIf { c -> demarrageLabel(c) != null } ?: ""
        )
    }
}

/** Informations d'affichage tirées des étiquettes OSM (JSON dans Station.params) ; rien n'est inventé : champ absent = null. */
object StationInfo {
    /** Clé interne (dans `params`, jamais affichée telle quelle) : date d'entrée de la borne dans la liste du projet, « AAAA-MM-JJ ». */
    const val ADDED_KEY = "_d"
    const val ADDED_DAYS = 45L

    /** Date d'entrée dans la liste si elle est connue ET date de moins de 45 jours ; sinon null. Comparaison faite à la lecture, jamais stockée. */
    fun recentlyAdded(s: Station, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate? {
        val d = try { java.time.LocalDate.parse(tags(s).optString(ADDED_KEY, "").take(10)) } catch (_: Exception) { return null }
        val age = java.time.temporal.ChronoUnit.DAYS.between(d, today)
        return d.takeIf { age in 0..ADDED_DAYS }
    }

    fun addedLabel(s: Station, today: java.time.LocalDate = java.time.LocalDate.now()): String? =
        recentlyAdded(s, today)?.let { "Ajoutée à la liste le %02d/%02d (entrée dans notre liste, pas forcément installée récemment)".format(it.dayOfMonth, it.monthValue) }

    private fun tags(s: Station): org.json.JSONObject = try { org.json.JSONObject(s.params.ifBlank { "{}" }) } catch (e: Exception) { org.json.JSONObject() }

    /** Puissance nominale en kW (valeur la plus élevée indiquée), ou null. Accepte « 22 kW », « 22000 » (W), « 11;22 ». */
    fun powerKw(s: Station): Double? {
        val t = tags(s)
        val vals = ArrayList<Double>()
        for (k in listOf("socket:type2:output", "socket:type2_cable:output", "charging_station:output", "maxpower")) {
            val raw = t.optString(k, "").trim()
            if (raw.isEmpty()) continue
            raw.split(";", "/").forEach { part ->
                val m = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(kw|w)?", RegexOption.IGNORE_CASE).find(part.trim()) ?: return@forEach
                var v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@forEach
                val unit = m.groupValues[2].lowercase()
                if (unit == "w" || (unit.isEmpty() && v >= 1000)) v /= 1000.0
                if (v in 0.5..400.0) vals.add(v)
            }
        }
        return vals.maxOrNull()
    }

    private fun kwText(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.FRANCE, "%.1f", v)

    /** Puissance : nominale (OpenStreetMap) si connue, sinon déclarée par la communauté (fiche), sinon la plus forte puissance relevée ; sinon « non renseignée ». */
    fun powerLabel(s: Station, ficheKw: Double? = null, observedKw: Double? = null): String =
        powerKw(s)?.let { "⚡ ${kwText(it)} kW (nominal)" }
            ?: ficheKw?.let { "⚡ ${kwText(it)} kW (déclaré)" }
            ?: observedKw?.let { "⚡ ${kwText(it)} kW (relevé, peut varier)" }
            ?: "⚡ puissance non renseignée"

    /** Nombre de points de charge d'après OpenStreetMap (étiquette capacity), ou null. */
    fun osmPoints(s: Station): Int? = tags(s).optString("capacity", "").trim().toIntOrNull()?.takeIf { it in 1..99 }

    /** « réservée aux clients » / « privée » d'après l'étiquette access, ou null si public ou inconnu. */
    fun accessLabel(s: Station): String? = when (tags(s).optString("access", "").lowercase()) {
        "customers" -> "Réservée aux clients"
        "private" -> "Accès privé"
        "permissive" -> "Accès toléré"
        "semipublic" -> "Semi-public (horaires limités)"
        else -> null
    }

    fun phases(s: Station): String? = tags(s).optString("phases", "").takeIf { it.isNotBlank() }

    /** Adresse d'après les étiquettes (OpenStreetMap : addr:*, Flandre : addr:full), ou null. */
    fun address(s: Station): String? {
        val t = tags(s)
        t.optString("addr:full", "").takeIf { it.isNotBlank() }?.let { return it }
        val street = listOf(t.optString("addr:street", ""), t.optString("addr:housenumber", "")).filter { it.isNotBlank() }.joinToString(" ")
        val city = listOf(t.optString("addr:postcode", ""), t.optString("addr:city", "")).filter { it.isNotBlank() }.joinToString(" ")
        return listOf(street, city).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
    }

    /** Origine de la fiche, d'après l'identifiant : liste OpenStreetMap, données ouvertes flamandes ; null pour les bornes de la communauté. */
    fun sourceLabel(s: Station): String? = when {
        s.id.startsWith("fl-") -> "Données ouvertes flamandes (Département MOW) / Eco-Movement"
        s.id.startsWith("bx-") -> Brussels.SOURCE
        s.id.startsWith("wal-") -> "SPW Wallonie (CC BY 4.0)"
        s.id.startsWith("osm-") -> "OpenStreetMap"
        else -> null
    }

    fun openingHours(s: Station): String? = tags(s).optString("opening_hours", "").takeIf { it.isNotBlank() }

    /** Dernière charge réussie pour la voiture donnée (date « ts » telle que stockée), et taux de réussite sur ses essais. */
    data class MyHistory(val lastOk: String?, val ok: Int, val n: Int)
    fun history(releves: List<Releve>, car: String): MyHistory {
        val mine = releves.filter { (canonicalCar(it.car) ?: DEFAULT_CAR) == car && !it.horsVerdict() }
        return MyHistory(mine.filter { it.result == Result.OK }.maxOfOrNull { it.ts }, mine.count { it.result == Result.OK }, mine.size)
    }
}


/** Date de l'extraction de la liste de bornes OpenStreetMap embarquée (affichée dans « À propos »). */
const val OSM_LIST_DATE = "20/11/2026"

/** États de borne signalés (hors verdict de compatibilité). « Déclaré » = rapporté par un tiers, non testé. */
enum class EtatKind(val code: String, val label: String, val ttlHours: Int, val declaredOnly: Boolean) {
    PANNE("panne", "En panne", 72, false),
    OCCUPEE("occupee", "Occupée", 3, false),
    FILE("file", "File d'attente", 3, false),
    LIBRE("libre", "Libre", 3, false),
    MARCHE("marche", "Fonctionne", 336, true),
    NEMARCHEPAS("nemarchepas", "Ne fonctionne pas", 336, true),
    /** « Panne réglée » (ou panne signalée par erreur) : annule les pannes plus anciennes de la même borne. Jamais affichée elle-même. Le serveur n'accepte que des ajouts : on annule par un nouvel état. */
    REPARE("repare", "Panne réglée", 72, false);
    companion object { fun of(code: String) = values().firstOrNull { it.code == code } }
}

data class Etat(val id: String, val stationId: String, val kind: EtatKind, val declared: Boolean, val car: String, val source: String, val ts: Long)

data class EtatLine(val kind: EtatKind, val declared: Boolean, val count: Int, val newestTs: Long)

object Etats {
    fun active(all: List<Etat>, now: Long): List<Etat> = all.filter { it.ts <= now + 3_600_000L && now - it.ts < it.kind.ttlHours * 3_600_000L }

    private fun day(ts: Long) = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

    /** Signalements actifs de la borne, sans ceux qu'un vrai relevé récent rend caducs (relevé du même jour ou plus récent). */
    fun shown(active: List<Etat>, stationId: String, releves: List<Releve>): List<Etat> {
        val mine = releves.filter { it.stationId == stationId }
        val repares = active.filter { it.stationId == stationId && it.kind == EtatKind.REPARE }
        return active.filter { e ->
            e.stationId == stationId && when {
                e.kind == EtatKind.REPARE -> false
                (e.kind == EtatKind.PANNE || e.kind == EtatKind.NEMARCHEPAS) && repares.any { it.ts >= e.ts } -> false
                e.kind == EtatKind.PANNE -> mine.none { it.result == Result.OK && it.ts > day(e.ts) } // seul un relevé d'un jour suivant lève la panne : un relevé du même jour peut être antérieur
                e.declared && e.kind.declaredOnly -> mine.none { it.ts >= day(e.ts) }
                else -> true
            }
        }
    }

    /** Regroupe par (état, déclaré ou non) ; l'état d'occupation le plus récent (occupée / file / libre) l'emporte sur les autres du même groupe. */
    fun lines(shown: List<Etat>): List<EtatLine> {
        val occ = setOf(EtatKind.OCCUPEE, EtatKind.FILE, EtatKind.LIBRE)
        val latestOcc = shown.filter { !it.declared && it.kind in occ }.maxByOrNull { it.ts }
        val kept = shown.filter { !(!it.declared && it.kind in occ) || it.kind == latestOcc?.kind }
        return kept.groupBy { it.kind to it.declared }.map { (k, v) -> EtatLine(k.first, k.second, v.size, v.maxOf { it.ts }) }
            .sortedWith(compareBy({ it.declared }, { it.kind.ordinal }))
    }

    /** « il y a 5 min », « il y a 2 h », « il y a 3 j ». */
    fun ago(ts: Long, now: Long): String {
        val m = maxOf(0L, (now - ts) / 60_000L)
        return when {
            m < 1 -> "à l'instant"
            m < 60 -> "il y a $m min"
            m < 48 * 60 -> "il y a ${m / 60} h"
            else -> "il y a ${m / 1440} j"
        }
    }

    /** « il y a 3 h (14:32) » : l'heure s'ajoute entre 1 h et 24 h, pour distinguer un état de ce matin d'un état d'hier. */
    fun agoAt(ts: Long, now: Long): String {
        val m = maxOf(0L, (now - ts) / 60_000L)
        val base = ago(ts, now)
        return if (m in 60 until 24 * 60) base + " (" + java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0) + ")" else base
    }

    fun lineText(l: EtatLine, now: Long): String {
        val n = if (l.count > 1) " · ${l.count} signalements" else ""
        return if (l.declared) "${if (l.kind == EtatKind.PANNE) "Déclarée en panne" else "Déclarée : " + l.kind.label.lowercase()} (non testé, non vérifié) · ${agoAt(l.newestTs, now)}$n"
        else "${l.kind.label} · ${agoAt(l.newestTs, now)}$n"
    }

    fun toJson(l: List<Etat>): String = org.json.JSONArray().also { a ->
        l.forEach { a.put(JSONObject().put("id", it.id).put("stationId", it.stationId).put("kind", it.kind.code).put("declared", it.declared).put("car", it.car).put("source", it.source).put("ts", it.ts)) }
    }.toString()

    fun fromJson(json: String): List<Etat> {
        val a = org.json.JSONArray(json)
        return (0 until a.length()).mapNotNull {
            val o = a.getJSONObject(it)
            val k = EtatKind.of(o.optString("kind", "")) ?: return@mapNotNull null
            Etat(o.getString("id"), o.getString("stationId"), k, o.optBoolean("declared", false), o.optString("car", ""), o.optString("source", ""), o.optLong("ts", 0L))
        }
    }

    /** Puissance saisie par l'utilisateur : virgule ou point, entre 0,5 et 400 kW ; sinon null. */
    fun parseKw(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.5..400.0 }
}


/** Pastille vue sur une borne (constat visuel, « verte », « blanche » ou « aucune »). Jamais comptée dans les relevés ni dans le verdict. `tested` : une voiture a été branchée ou non. */
data class Pastille(val id: String, val stationId: String, val code: String, val tested: Boolean, val ts: Long)

object Pastilles {
    private fun day(ts: Long) = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

    /** Même borne, même pastille, même jour : une seule fois par téléphone. */
    fun isDup(mine: List<Pastille>, stationId: String, code: String, at: Long) = mine.any { it.stationId == stationId && it.code == code && day(it.ts) == day(at) }

    /** Nombre de téléphones distincts par (borne, code), lu sur la vue serveur « pastilles_par_borne » ; clé « stationId|code ». */
    fun phonesFromRows(json: String): Map<String, Int> {
        val a = org.json.JSONArray(json); val out = HashMap<String, Int>()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); val c = o.optString("code", ""); if (pastilleLabel(c) != null) out[o.getString("station_id") + "|" + c] = o.optInt("n_telephones", 0) }
        return out
    }

    /** « vue 1 fois » (non confirmée) ou « confirmée par 2 téléphones » ; constat visuel, jamais un verdict. null si aucune observation. */
    fun summary(all: List<Pastille>, stationId: String, phones: Map<String, Int> = emptyMap()): String? {
        val l = all.filter { it.stationId == stationId && pastilleLabel(it.code) != null }
        if (l.isEmpty()) return null
        val by = l.groupBy { it.code }
        val names = mapOf("verte" to "vert", "blanche" to "blanc à contour rouge", "bleue" to "bleu EnergyDrive", "autre" to "d'un autre type")
        val parts = by.entries.sortedByDescending { it.value.size }.map { (c, v) ->
            val tel = phones[stationId + "|" + c] // téléphones distincts (vue serveur) ; null = inconnu : on compte les lignes, sans jamais écrire « confirmée »
            val conf = if (tel != null && tel >= 2) "confirmé par $tel téléphones" else "vu ${if (tel != null) 1 else v.size} fois, non confirmé"
            if (c == "aucune") "aucun sticker constaté ($conf)" else "sticker ${names[c]} $conf"
        }
        val last = day(l.maxOf { it.ts })
        val tested = l.count { it.tested }
        return parts.joinToString(" · ") + " (dernier : $last)" + (if (by.containsKey("blanche") && by.keys.any { it == "verte" || it == "bleue" }) " · avis partagés" else "") + " · " + (if (tested > 0) "$tested avec voiture branchée, " else "") + "constat visuel, non vérifié"
    }

    /** Repère carte des autocollants verts/bleus vus : 1 = vue 1 fois, 2 = confirmée par 2 téléphones ou plus ; absent si des constats contraires (blanche) datent de moins de 90 jours.
     *  Constat visuel : jamais une couleur de verdict ni un anneau. */
    fun markers(all: List<Pastille>, phones: Map<String, Int>, nowMs: Long): Map<String, Int> {
        val out = HashMap<String, Int>()
        val ninety = 90L * 24 * 3600 * 1000
        for ((sid, l) in all.filter { pastilleLabel(it.code) != null }.groupBy { it.stationId }) {
            val fav = l.filter { it.code == "verte" || it.code == "bleue" }
            if (fav.isEmpty()) continue
            if (l.any { it.code == "blanche" && nowMs - it.ts < ninety }) continue
            val tel = fav.groupBy { it.code }.keys.maxOf { c -> phones[sid + "|" + c] ?: 0 }
            out[sid] = if (tel >= 2) 2 else 1
        }
        return out
    }

    fun toJson(l: List<Pastille>): String = org.json.JSONArray().also { a ->
        l.forEach { a.put(JSONObject().put("id", it.id).put("stationId", it.stationId).put("code", it.code).put("tested", it.tested).put("ts", it.ts)) }
    }.toString()

    fun fromJson(json: String): List<Pastille> {
        val a = org.json.JSONArray(json)
        return (0 until a.length()).mapNotNull {
            val o = a.getJSONObject(it)
            val c = o.optString("code", "")
            if (pastilleLabel(c) == null) null else Pastille(o.getString("id"), o.getString("stationId"), c, o.optBoolean("tested", false), o.optLong("ts", 0L))
        }
    }
}

/** Fiche de borne remplie par la communauté : nombre de bornes (points de charge) à cet endroit, prises par borne, puissance déclarée. Chaque champ est facultatif. */
data class Fiche(val id: String, val stationId: String, val name: String, val lat: Double, val lon: Double, val points: Int?, val plugs: Int?, val kw: Double?, val ts: Long, val contact: String? = null, val pastille: String? = null)

object Fiches {
    /** Pour chaque borne, la valeur la plus récente de chaque champ renseigné. */
    fun merge(all: List<Fiche>): Map<String, Fiche> =
        all.groupBy { it.stationId }.mapValues { (_, v) ->
            val l = v.sortedByDescending { it.ts }
            Fiche(l[0].id, l[0].stationId, l[0].name, l[0].lat, l[0].lon, l.firstNotNullOfOrNull { it.points }, l.firstNotNullOfOrNull { it.plugs }, l.firstNotNullOfOrNull { it.kw }, l[0].ts, l.firstNotNullOfOrNull { it.contact }, l.firstNotNullOfOrNull { it.pastille })
        }

    /** Numéro d'assistance lu sur la borne : 6 à 20 caractères parmi chiffres, espaces, +, ., /, -, (). Sinon null. */
    fun parsePhone(text: String): String? {
        val t = text.trim()
        val digits = t.filter { it.isDigit() }.let { if (t.startsWith("+32")) "0" + it.drop(2) else if (it.startsWith("0032")) "0" + it.drop(4) else it }
        val premium = digits.startsWith("090") || digits.startsWith("070") || digits.startsWith("078") // numéros surtaxés ou à tarif spécial
        return t.takeIf { it.length in 6..20 && Regex("[0-9+().\\-/ ]+").matches(it) && it.count { c -> c.isDigit() } >= 6 && !premium }
    }

    fun parseCount(text: String, max: Int): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..max }

    /** « 2 bornes · 2 prises par borne ». */
    fun describe(points: Int?, plugs: Int?): String? {
        val a = points?.let { "$it borne${if (it > 1) "s" else ""}" }
        val b = plugs?.let { "$it prise${if (it > 1) "s" else ""} par borne" }
        return listOfNotNull(a, b).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun toJson(l: List<Fiche>): String = org.json.JSONArray().also { a ->
        l.forEach { a.put(JSONObject().put("id", it.id).put("stationId", it.stationId).put("name", it.name).put("lat", it.lat).put("lon", it.lon)
            .also { o -> it.points?.let { v -> o.put("points", v) }; it.plugs?.let { v -> o.put("plugs", v) }; it.kw?.let { v -> o.put("kw", v) }; it.contact?.let { v -> o.put("contact", v) }; it.pastille?.let { v -> o.put("pastille", v) } }.put("ts", it.ts)) }
    }.toString()

    fun fromJson(json: String): List<Fiche> {
        val a = org.json.JSONArray(json)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Fiche(o.getString("id"), o.getString("stationId"), o.optString("name", ""), o.optDouble("lat", 0.0), o.optDouble("lon", 0.0),
                if (o.has("points")) o.optInt("points") else null, if (o.has("plugs")) o.optInt("plugs") else null,
                if (o.has("kw")) o.optDouble("kw").takeIf { v -> !v.isNaN() } else null, o.optLong("ts", 0L), o.optString("contact", "").ifBlank { null }, o.optString("pastille", "").takeIf { c -> pastilleLabel(c) != null })
        }
    }

    /** Bornes connues seulement par une fiche de la communauté (borne absente d'OpenStreetMap, ajoutée par un utilisateur). */
    fun extraStations(fiches: List<Fiche>, knownIds: Set<String>): List<Station> =
        fiches.filter { it.stationId !in knownIds && it.lat != 0.0 }.distinctBy { it.stationId }
            .map { Station(it.stationId, it.name.ifBlank { "Borne ajoutée" }, "", it.lat, it.lon, "AC", "", "commu") }
}

/** Indice de voisinage (hypothèse) : une borne sans essai pour ma voiture, à moins de 50 m d'une autre borne qui en a, partage peut-être son raccordement. Jamais un verdict. */
object Voisins {
    const val RAYON_M = 50.0
    fun hint(s: Station, stations: Collection<Station>, releves: List<Releve>, car: String): String? {
        fun mineOf(id: String) = releves.filter { it.stationId == id && !it.horsVerdict() && (canonicalCar(it.car) ?: DEFAULT_CAR) == car }
        if (mineOf(s.id).isNotEmpty()) return null
        val near = stations.filter { it.id != s.id && it.kind != "privee" && Geo.distanceM(s.lat, s.lon, it.lat, it.lon) <= RAYON_M }
        val pairs = near.mapNotNull { n -> mineOf(n.id).takeIf { it.isNotEmpty() }?.let { n to it } }
        if (pairs.isEmpty()) return null
        val ok = pairs.any { (_, r) -> r.any { it.result == Result.OK } }
        val ko = pairs.any { (_, r) -> r.any { it.result == Result.REFUS } }
        val d = pairs.minOf { (n, _) -> Geo.distanceM(s.lat, s.lon, n.lat, n.lon) }.toInt()
        val what = when { ok && ko -> "des avis opposés"; ok -> "✓ ça charge"; else -> "✕ ne charge pas" }
        return "Même site probable : à $d m, une autre borne a donné $what avec ce modèle de voiture, selon des conducteurs (hypothèse : deux bornes voisines n'ont pas forcément le même raccordement ; ce n'est pas un essai sur celle-ci)."
    }
}
