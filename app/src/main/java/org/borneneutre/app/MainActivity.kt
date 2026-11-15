package org.borneneutre.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.time.LocalDate
import java.util.UUID

/** Fond de carte principal : OpenFreeMap (gratuit, sans clé ; attribution obligatoire). Repli : tuiles OpenStreetMap. */
private const val OFM_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val OSM_STYLE = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19,"attribution":"© OpenStreetMap"}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B7F5B))) { App() }
        }
    }
}

fun stationsGeoJson(stations: List<Station>, releves: List<Releve>, ev: List<Evidence>, priors: Map<String, Double>): String {
    val feats = JSONArray()
    for (s in stations) {
        val mine = releves.filter { it.stationId == s.id }
        val st = if (mine.isNotEmpty()) simpleStatus(mine) else {
            val e = Estimation.estimate(s.lat, s.lon, ev, priors[s.id])
            SimpleStatus("Pas encore essayée : " + e.label.lowercase(), if (e.confidence < 0.3) "#6b7280" else if (e.p >= 0.65) "#8fd19e" else if (e.p <= 0.35) "#f4a6a6" else "#c9cdd2", 0, false)
        }
        val f = JSONObject()
        f.put("type", "Feature")
        f.put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(s.lon).put(s.lat)))
        f.put("properties", JSONObject().put("id", s.id).put("color", st.color).put("r", if (st.tried) 10 else 6))
        feats.put(f)
    }
    return JSONObject().put("type", "FeatureCollection").put("features", feats).toString()
}

fun gridGeoJson(cells: List<GridCell>): String {
    val feats = JSONArray()
    for (c in cells) {
        val ring = JSONArray()
            .put(JSONArray().put(c.lon0).put(c.lat0)).put(JSONArray().put(c.lon1).put(c.lat0))
            .put(JSONArray().put(c.lon1).put(c.lat1)).put(JSONArray().put(c.lon0).put(c.lat1))
            .put(JSONArray().put(c.lon0).put(c.lat0))
        val f = JSONObject()
        f.put("type", "Feature")
        f.put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring)))
        f.put("properties", JSONObject().put("color", Estimation.colorFor(c.est.p)).put("op", minOf(0.45, 0.12 + c.est.confidence * 0.35)))
        feats.put(f)
    }
    return JSONObject().put("type", "FeatureCollection").put("features", feats).toString()
}

fun pointGeoJson(p: LatLng?): String {
    if (p == null) return EMPTY
    return JSONObject().put("type", "FeatureCollection").put(
        "features", JSONArray().put(
            JSONObject().put("type", "Feature").put("properties", JSONObject())
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.longitude).put(p.latitude)))
        )
    ).toString()
}


/** Un relevé en attente de confirmation : résultat + bornes candidates les plus proches de la position GPS. */
/** phase : 0 = attente du GPS, 1 = recherche de la borne, 2 = prêt. */
data class Pending(val result: Result, val lat: Double, val lon: Double, val candidates: List<Pair<Station, Double>>, val phase: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val seedStations = remember { Store.loadSeedStations(ctx) }
    val seedReleves = remember { Store.loadSeedReleves(ctx) }
    val priors = remember { Store.loadPriors(ctx) }
    val userInit = remember { Store.loadUser(ctx) }
    var userStations by remember { mutableStateOf(userInit.first) }
    var userReleves by remember { mutableStateOf(userInit.second) }
    var osmStations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var commuStations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var commuReleves by remember { mutableStateOf<List<Releve>>(emptyList()) }
    val stations = remember(userStations, commuStations) { (seedStations + userStations).let { base -> base + commuStations.filter { c -> base.none { it.id == c.id } } } }
    val releves = remember(userReleves, commuReleves) { seedReleves + userReleves + commuReleves }
    val byId = remember(stations, osmStations) { (osmStations + stations).associateBy { it.id } }
    var myCar by remember { mutableStateOf(Store.loadCar(ctx)) }

    val ev = remember(releves, byId) { Estimation.evidenceFrom(releves, byId) }
    var cells by remember { mutableStateOf<List<GridCell>>(emptyList()) }
    LaunchedEffect(ev) { cells = withContext(Dispatchers.Default) { Estimation.grid(ev) } }
    var showGrid by remember { mutableStateOf(true) }
    var tapEstimate by remember { mutableStateOf<Pair<LatLng, Estimate>?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var showFaq by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(!ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("welcomed", false)) }
    var showCar by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var myPos by remember { mutableStateOf<LatLng?>(null) }

    val prefs = remember { ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(prefs.getInt("display_mode", 0)) }
    var autoRefresh by remember { mutableStateOf(prefs.getBoolean("auto_refresh", false)) }
    var showModes by remember { mutableStateOf(false) }
    var screenOsm by remember { mutableStateOf<List<Station>>(emptyList()) }
    var cacheOsm by remember { mutableStateOf<List<Station>>(emptyList()) }
    var loadingScreen by remember { mutableStateOf(false) }

    val visible = remember(stations, osmStations, mode, screenOsm, cacheOsm, myPos, releves, byId) {
        val extra = osmStations.filter { o -> stations.none { Geo.distanceM(o.lat, o.lon, it.lat, it.lon) < 25 } }
        val base = (stations + extra).filter { it.current != "DC" }
        fun withOsm(): List<Station> {
            val more = (screenOsm + cacheOsm).distinctBy { it.id }.filter { o -> base.none { Geo.distanceM(o.lat, o.lon, it.lat, it.lon) < 25 } }
            return (base + more).filter { it.current != "DC" }
        }
        when (mode) {
            1 -> { val tried = releves.mapNotNull { byId[it.stationId] }.distinctBy { it.id }
                   base.filter { s -> tried.any { Geo.distanceM(s.lat, s.lon, it.lat, it.lon) <= 1000 } } }
            2 -> withOsm()
            3 -> { val pos = myPos; val done = releves.map { it.stationId }.toSet()
                   if (pos == null) emptyList() else withOsm().filter { it.id !in done && Geo.distanceM(pos.latitude, pos.longitude, it.lat, it.lon) <= 2000 } }
            else -> base
        }
    }
    val nReleves = remember(releves, byId) { releves.filter { byId[it.stationId]?.kind != "privee" } }
    val nBornes = remember(nReleves) { nReleves.map { it.stationId }.distinct().size }
    val stationsJson = remember(visible, releves, ev, priors) { stationsGeoJson(visible, releves, ev, priors) }
    val gridJson = remember(cells, showGrid) { if (showGrid) gridGeoJson(cells) else EMPTY }

    LaunchedEffect(style, stationsJson, gridJson, myPos) {
        style?.getSourceAs<GeoJsonSource>("grid")?.setGeoJson(gridJson)
        style?.getSourceAs<GeoJsonSource>("stations")?.setGeoJson(stationsJson)
        style?.getSourceAs<GeoJsonSource>("me")?.setGeoJson(pointGeoJson(myPos))
    }

    fun hasLocation() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Position GPS : dernière position connue si elle a moins de 60 s, sinon une mise à jour unique (délai 12 s). */
    fun locate(onFail: () -> Unit = {}, onFix: (Location) -> Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            try { lm.isProviderEnabled(it) } catch (_: Exception) { false }
        }
        if (providers.isEmpty()) { Toast.makeText(ctx, "Activez la localisation du téléphone.", Toast.LENGTH_LONG).show(); onFail(); return }
        try {
            val now = System.currentTimeMillis()
            // Position récente et assez précise : on l'utilise tout de suite.
            val fresh = providers.mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: SecurityException) { null } }
                .filter { now - it.time < 60_000 && it.accuracy <= 50f }.minByOrNull { it.accuracy }
            if (fresh != null) { onFix(fresh); return }
            var done = false
            var best: Location? = null
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val listeners = ArrayList<android.location.LocationListener>()
            fun finish(loc: Location?) {
                if (done) return
                done = true
                listeners.forEach { lm.removeUpdates(it) }
                if (loc != null && loc.accuracy <= 150f) onFix(loc)
                else { Toast.makeText(ctx, "Position GPS trop imprécise ou introuvable. Réessayez à l'extérieur.", Toast.LENGTH_LONG).show(); onFail() }
            }
            providers.forEach { p ->
                val l = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (best == null || location.accuracy < best!!.accuracy) best = location
                        if (location.accuracy <= 50f) finish(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                listeners.add(l)
                @Suppress("DEPRECATION")
                try { lm.requestLocationUpdates(p, 0L, 0f, l, android.os.Looper.getMainLooper()) } catch (_: SecurityException) {}
            }
            handler.postDelayed({ finish(best) }, 15_000)
        } catch (_: SecurityException) {
        }
    }

    fun showMe() = locate { loc ->
        val ll = LatLng(loc.latitude, loc.longitude)
        myPos = ll
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16.0))
    }

    var reportToken by remember { mutableStateOf(0L) }

    /** Relevé en un geste : GPS (15 s max) → bornes connues proches → recherche OpenStreetMap (10 s max) → sinon nouvelle borne. */
    fun startReport(result: Result) {
        val token = System.nanoTime()
        reportToken = token
        pending = Pending(result, 0.0, 0.0, emptyList(), 0)
        locate(onFail = { if (reportToken == token) pending = null }) { loc ->
            if (reportToken != token) return@locate
            val ll = LatLng(loc.latitude, loc.longitude)
            myPos = ll
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 17.0))
            val known = Overpass.candidates(loc.latitude, loc.longitude, stations, osmStations, 60.0)
            pending = Pending(result, loc.latitude, loc.longitude, known, 1)
            scope.launch {
                val job = async(Dispatchers.IO) { Overpass.fetch(loc.latitude, loc.longitude, 80) }
                val found = withTimeoutOrNull(10_000) { job.await() }
                if (reportToken != token) return@launch
                if (found != null) osmStations = (osmStations + found).distinctBy { it.id }
                val cands = Overpass.candidates(loc.latitude, loc.longitude, stations, osmStations, 60.0)
                pending = pending?.copy(candidates = cands, phase = 2)
            }
        }
    }

    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val todo = afterPermission
        afterPermission = null
        if (grants.values.any { it }) (todo ?: { showMe() })()
    }
    fun requireLocation(then: () -> Unit) {
        if (hasLocation()) then()
        else { afterPermission = then; permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }

    fun loadScreen(lat: Double, lon: Double, radius: Int) {
        if (loadingScreen) return
        loadingScreen = true
        scope.launch {
            val found = withTimeoutOrNull(75_000) { withContext(Dispatchers.IO) { Overpass.fetch(lat, lon, radius) } }
            loadingScreen = false
            if (found == null) Toast.makeText(ctx, "Serveur OpenStreetMap injoignable (${Overpass.lastError.ifBlank { "délai dépassé" }}). Réessayez plus tard.", Toast.LENGTH_LONG).show()
            else screenOsm = (screenOsm + found).distinctBy { it.id }
        }
    }

    fun chooseMode(m: Int) {
        mode = m
        prefs.edit().putInt("display_mode", m).apply()
        if (m == 2) {
            val cp = map?.cameraPosition
            val t = cp?.target
            if (cp != null && t != null && cp.zoom >= 12.0) loadScreen(t.latitude, t.longitude, 1500)
            else Toast.makeText(ctx, "Zoomez sur la zone voulue (rue ou quartier), puis choisissez de nouveau ce mode.", Toast.LENGTH_LONG).show()
        }
        if (m == 3) requireLocation { locate { loc -> myPos = LatLng(loc.latitude, loc.longitude); loadScreen(loc.latitude, loc.longitude, 2000) } }
    }

    // Liste mensuelle (case « Actualiser la liste de bornes ») : une seule requête par mois, zone Bruxelles-Enghien, gardée sur le téléphone.
    LaunchedEffect(autoRefresh) {
        val f = java.io.File(ctx.filesDir, "osm-cache.json")
        if (f.exists()) cacheOsm = withContext(Dispatchers.IO) { runCatching { Overpass.parse(f.readText()) }.getOrDefault(emptyList()) }
        if (autoRefresh && System.currentTimeMillis() - prefs.getLong("last_refresh", 0L) > 30L * 24 * 3600 * 1000) {
            val raw = withContext(Dispatchers.IO) { Overpass.fetchBox(50.55, 3.9, 51.05, 4.7) }
            if (raw != null) {
                val parsed = runCatching { Overpass.parse(raw) }.getOrNull()
                if (parsed != null && parsed.isNotEmpty()) {
                    withContext(Dispatchers.IO) { f.writeText(raw) }
                    cacheOsm = parsed
                    prefs.edit().putLong("last_refresh", System.currentTimeMillis()).apply()
                }
            }
        }
    }

    LaunchedEffect(map) {
        if (map != null && hasLocation()) showMe()
    }

    var syncStatus by remember { mutableStateOf("") }
    fun runSync() {
        scope.launch {
            val local = (seedStations + userStations).associateBy { it.id }
            val res = withContext(Dispatchers.IO) { Sync.sync(ctx, userReleves, local + osmStations.associateBy { it.id }) } .also { syncStatus = Sync.status } ?: run { syncStatus = Sync.status; return@launch }
            val parsed = runCatching { Sync.fromRows(res.first, (seedReleves + userReleves).map { it.id }.toSet(), local.keys) }.getOrNull() ?: return@launch
            commuStations = parsed.first
            commuReleves = parsed.second
            Store.saveCommunity(ctx, res.first)
        }
    }
    var lastSaved by remember { mutableStateOf<String?>(null) }
    fun saveReleve(stationId: String?, lat: Double, lon: Double, result: Result, newName: String = "", share: Boolean = true) {
        var sid = stationId
        var us = userStations
        if (sid == null) {
            sid = "user-" + UUID.randomUUID().toString().take(12)
            us = us + Station(sid, newName.trim().ifBlank { "Nouvelle borne" }, "", lat, lon, "AC", "", if (share) "user" else "privee")
        } else if (seedStations.none { it.id == sid } && us.none { it.id == sid }) {
            val o = byId[sid]
            if (o != null) us = us + o
        }
        val rid = "u" + UUID.randomUUID().toString().replace("-", "").take(16)
        val ur = userReleves + Releve(rid, sid, LocalDate.now().toString(), result, null, "", false, myCar)
        userStations = us
        userReleves = ur
        Store.saveUser(ctx, us, ur)
        selectedId = sid
        lastSaved = rid
    }
    var secondsLeft by remember { mutableStateOf(0) }
    // Envoi différé de 8 s avec décompte visible : le temps d'annuler une erreur.
    LaunchedEffect(lastSaved) {
        if (lastSaved == null) return@LaunchedEffect
        secondsLeft = 8
        while (secondsLeft > 0) { delay(1000); secondsLeft-- }
        lastSaved = null
        runSync()
    }
    fun undoLast() {
        val rid = lastSaved ?: return
        userReleves = userReleves.filter { it.id != rid }
        Store.saveUser(ctx, userStations, userReleves)
        lastSaved = null
    }

    LaunchedEffect(Unit) {
        // Copie locale des relevés partagés : la carte reste complète hors ligne.
        Store.loadCommunity(ctx)?.let { txt ->
            runCatching { Sync.fromRows(txt, (seedReleves + userReleves).map { it.id }.toSet(), (seedStations + userStations).map { it.id }.toSet()) }.getOrNull()?.let { commuStations = it.first; commuReleves = it.second }
        }
        runSync()
    }

    Box(Modifier.fillMaxSize()) {
        MapContainer(
            modifier = Modifier.fillMaxSize(),
            onReady = { m, st -> map = m; style = st },
            onStationTap = { id -> selectedId = id; tapEstimate = null },
            onMapTap = { ll -> selectedId = null; tapEstimate = Pair(ll, Estimation.estimate(ll.latitude, ll.longitude, ev)) }
        )

        Text(
            "${nReleves.size} relevés · $nBornes bornes couvertes${if (syncStatus.isNotBlank()) " · $syncStatus" else ""} · © OpenStreetMap contributors · OpenFreeMap · v${BuildConfig.VERSION_NAME}",
            fontSize = 9.sp, color = Color.DarkGray,
            modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)).padding(start = 8.dp, bottom = 96.dp, end = 80.dp).background(Color(0xCCFFFFFF)).padding(horizontal = 4.dp)
        )

        Column(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = false, onClick = { showCar = true }, modifier = Modifier.weight(1f, fill = false), label = { Text(myCar, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) })
                FilterChip(selected = false, onClick = { showAbout = true }, label = { Text("ⓘ") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = showGrid, onClick = { showGrid = !showGrid }, label = { Text("Zones") })
                FilterChip(selected = mode != 0, onClick = { showModes = true }, label = { Text(if (loadingScreen) "Bornes…" else "Bornes") })
                FilterChip(selected = false, onClick = { showFaq = true }, label = { Text("FAQ") })
            }
            Column(
                Modifier.padding(top = 6.dp).clip(MaterialTheme.shapes.medium).background(Color(0xE6FFFFFF)).padding(horizontal = 6.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Legend("#1b9e3a", "ça charge"); Legend("#d62728", "ne charge pas"); Legend("#f28e2b", "essais opposés")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Legend("#6b7280", "pas essayée")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Zones :", fontSize = 11.sp, maxLines = 1, softWrap = false)
                    for (z in LEGEND_ZONES) Legend(z.color, z.text, square = true)
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FloatingActionButton(
                onClick = { requireLocation { showMe() } },
                modifier = Modifier.align(Alignment.End).size(60.dp),
                shape = CircleShape, containerColor = Color(0xFF1A73E8), contentColor = Color.White
            ) { LocateIcon() }
            val sel = selectedId?.let { byId[it] }
            if (sel != null) {
                StationCard(sel, releves, myCar, onClose = { selectedId = null },
                    onReport = { r -> saveReleve(sel.id, sel.lat, sel.lon, r) })
            }
            if (lastSaved != null) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Relevé enregistré ✓ · envoi dans $secondsLeft s", Modifier.weight(1f))
                        TextButton(onClick = { undoLast() }) { Text("Annuler") }
                    }
                }
            }
            val te = tapEstimate
            if (sel == null && te != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Estimation à cet endroit", style = MaterialTheme.typography.titleSmall)
                        Text("${te.second.label} · ${te.second.zone.label}")
                        Text("Estimation expérimentale, fondée sur les relevés voisins : aucune garantie.", fontSize = 11.sp, color = Color.Gray)
                        TextButton(onClick = { tapEstimate = null }) { Text("Fermer") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { requireLocation { startReport(Result.OK) } },
                    modifier = Modifier.weight(1f).height(64.dp), shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B9E3A))
                ) { Text("✅ Ça charge", fontSize = 15.sp, maxLines = 1, softWrap = false) }
                Button(
                    onClick = { requireLocation { startReport(Result.REFUS) } },
                    modifier = Modifier.weight(1f).height(64.dp), shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD62728))
                ) { Text("❌ Ne charge pas", fontSize = 15.sp, maxLines = 1, softWrap = false) }
            }
        }
    }

    if (showWelcome) AlertDialog(
        onDismissRequest = {},
        title = { Text("Bienvenue") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Votre voiture :", style = MaterialTheme.typography.labelLarge)
                CARS.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = myCar == c, onClick = { myCar = c })
                        Text(c, fontSize = 14.sp)
                    }
                }
                Text("Smart : cette application ne concerne que le chargeur 22 kW triphasé (option d'origine), pas les versions 4,6 kW. Ne comptez jamais sur une seule borne : gardez une marge de batterie.", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Après une recharge, appuyez sur « Ça charge » ou « Ne charge pas » : l'app trouve la borne grâce au GPS. Votre relevé (borne, résultat, date, modèle) est partagé avec les autres conductrices et conducteurs ; jamais votre nom ni votre trajet.", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = {
            Store.saveCar(ctx, myCar)
            ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putBoolean("welcomed", true).apply()
            showWelcome = false
        }) { Text("Commencer") } }
    )

    if (showModes) AlertDialog(
        onDismissRequest = { showModes = false },
        title = { Text("Bornes affichées") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val opts = listOf("Liste actuelle (par défaut)", "Seulement les bornes à moins de 1 km d'un relevé", "Toutes les bornes OpenStreetMap de la zone affichée (réseau requis)", "À essayer près de moi (bornes sans relevé, 2 km)")
                opts.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == i, onClick = { chooseMode(i); showModes = false })
                        Text(t, fontSize = 14.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    androidx.compose.material3.Checkbox(checked = autoRefresh, onCheckedChange = { autoRefresh = it; prefs.edit().putBoolean("auto_refresh", it).apply() })
                    Text("Actualiser la liste de bornes (une fois par mois, zone Bruxelles-Enghien ; télécharge environ 2 Mo depuis OpenStreetMap)", fontSize = 12.sp)
                }
                Text("Pour une autre zone du mode « zone affichée » : zoomez, puis rechoisissez ce mode.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { showModes = false }) { Text("Fermer") } }
    )

    if (showFaq) FaqDialog(onDismiss = { showFaq = false })

    if (showCar) CarDialog(myCar, onDismiss = { showCar = false }) { c -> myCar = c; Store.saveCar(ctx, c); showCar = false }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    pending?.let { p ->
        ConfirmDialog(p, myCar, onDismiss = { pending = null }) { stationId, name, share ->
            saveReleve(stationId, p.lat, p.lon, p.result, name, share)
            pending = null
        }
    }
}

/** Cible de géolocalisation dessinée (sans dépendance d'icônes). */
@Composable
fun LocateIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(30.dp)) {
        val c = center; val r = size.minDimension * 0.30f; val w = size.minDimension * 0.09f
        drawCircle(Color.White, radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawCircle(Color.White, radius = size.minDimension * 0.12f, center = c)
        val o = size.minDimension * 0.5f; val i = r + w
        listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f).forEach { (dx, dy) ->
            drawLine(Color.White, androidx.compose.ui.geometry.Offset(c.x + dx * i, c.y + dy * i), androidx.compose.ui.geometry.Offset(c.x + dx * o, c.y + dy * o), strokeWidth = w)
        }
    }
}

/** Entrées « zones » de la légende : toujours des carrés arrondis, aux teintes pâles de la carte (vérifié par un test). */
data class LegendEntry(val color: String, val text: String, val square: Boolean)
val LEGEND_ZONES = listOf(LegendEntry("#bfe0c0", "estimée favorable", true), LegendEntry("#eebdbd", "défavorable", true))

@Composable
fun Legend(color: String, text: String, square: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(if (square) androidx.compose.foundation.shape.RoundedCornerShape(2.dp) else CircleShape).background(Color(android.graphics.Color.parseColor(color))))
        Text(" $text", fontSize = 11.sp, maxLines = 1, softWrap = false)
    }
}


@Composable
fun StationCard(s: Station, releves: List<Releve>, myCar: String, onClose: () -> Unit, onReport: (Result) -> Unit) {
    val ctx = LocalContext.current
    val mine = releves.filter { it.stationId == s.id }
    val st = simpleStatus(mine)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium)
            if (s.operator.isNotBlank()) Text(s.operator, fontSize = 12.sp, color = Color.Gray)
            Text(st.label, color = Color(android.graphics.Color.parseColor(st.color)), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.heightIn(max = 90.dp).verticalScroll(rememberScrollState())) {
                mine.sortedByDescending { it.ts }.forEach {
                    val r = if (it.result == Result.OK) "✓ ça charge" else if (it.result == Result.REFUS) "✕ ne charge pas" else "limité"
                    val c = canonicalCar(it.car) ?: DEFAULT_CAR
                    Text("• ${it.ts} : $r" + if (c != myCar) " ($c)" else "", fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onReport(Result.OK) }) { Text("✅ Ça charge ici") }
                TextButton(onClick = { onReport(Result.REFUS) }) { Text("❌ Ne charge pas ici") }
            }
            Row {
                TextButton(onClick = {
                    val uri = Uri.parse("geo:${s.lat},${s.lon}?q=${s.lat},${s.lon}(${Uri.encode(s.name)})")
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }) { Text("Itinéraire") }
                TextButton(onClick = onClose) { Text("Fermer") }
            }
        }
    }
}

@Composable
fun Countdown(total: Int, key: Any): Int {
    var left by remember(key) { mutableStateOf(total) }
    LaunchedEffect(key) { while (left > 0) { delay(1000); left-- } }
    return left
}

@Composable
fun ConfirmDialog(p: Pending, car: String, onDismiss: () -> Unit, onConfirm: (String?, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var share by remember { mutableStateOf(true) }
    var chosen by remember(p.candidates) { mutableStateOf<String?>(p.candidates.firstOrNull()?.first?.id) }
    var isNew by remember(p.candidates) { mutableStateOf(p.candidates.isEmpty()) }
    val verdict = if (p.result == Result.OK) "✅ Ça charge" else "❌ Ne charge pas"
    val left = Countdown(if (p.phase == 0) 15 else 10, p.phase)
    val ready = p.phase == 2 || (p.phase == 1 && p.candidates.isNotEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(verdict) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Voiture : $car", fontSize = 12.sp, color = Color.Gray)
                if (p.phase == 0) {
                    Text("1/2 Position GPS… ${left} s", modifier = Modifier.padding(top = 8.dp))
                    LinearProgressIndicator(progress = { (15 - left) / 15f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Text("Placez-vous près de la borne, à l'extérieur si possible. « Enregistrer » s'activera dès que la position est connue.", fontSize = 12.sp)
                } else {
                    if (p.phase == 1) {
                        Text("2/2 Recherche de la borne sur OpenStreetMap… ${left} s", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        LinearProgressIndicator(progress = { (10 - left) / 10f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        if (!ready) Text("« Enregistrer » s'activera dès qu'une borne est trouvée, ou après ce délai.", fontSize = 12.sp)
                    }
                    Text("Borne :", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    p.candidates.take(4).forEach { (s, d) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !isNew && chosen == s.id, onClick = { isNew = false; chosen = s.id })
                            Text("${s.name} (${d.toInt()} m)", fontSize = 13.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isNew, onClick = { isNew = true })
                        Text("Nouvelle borne à ma position", fontSize = 13.sp)
                    }
                    if (isNew) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom ou adresse (facultatif)") }, singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = share, onCheckedChange = { share = it })
                            Text("Partager cette borne avec les autres (décochez pour une borne privée ou à domicile)", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(if (isNew) null else chosen, name, share) }, enabled = ready) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bornes Smart et Zoé") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${BuildConfig.VERSION_NAME} · licence GPL-3.0", fontSize = 12.sp, color = Color.Gray)
                Text("Cette application rassemble des relevés d'utilisateurs. Elle ne donne aucune garantie : une borne qui a chargé une fois peut refuser ensuite, et inversement. Elle ne remplace pas l'avis d'un électricien ni celui du constructeur. Ne modifiez jamais votre installation électrique sans un professionnel qualifié.", fontSize = 13.sp)
                Text("Vos relevés (borne, résultat, date, modèle de voiture) sont partagés anonymement : ni nom, ni plaque, ni trajet. Voir la FAQ « Que deviennent mes relevés ? ».", fontSize = 13.sp)
                Text("Projet indépendant, non affilié à Renault ni à Mercedes-Benz / smart. Cartes © contributeurs OpenStreetMap, fond OpenFreeMap.", fontSize = 12.sp)
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/stabilo3k/bornes-smart-zoe"))) }) { Text("Code source et signalement d'un problème") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

@Composable
fun CarDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ma voiture") },
        text = {
            Column {
                Text("L'app ne s'adresse qu'aux voitures signalées pour ce problème de recharge.", fontSize = 12.sp)
                CARS.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = text == c, onClick = { text = c })
                        Text(c, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun FaqDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember { runCatching { Faq.load(ctx) }.getOrDefault(emptyList()) }
    var open by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FAQ : bornes, réseau et recharge") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isEmpty()) Text("FAQ indisponible.")
                Text("Bornes Smart et Zoé · version ${BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = Color.Gray)
                items.forEach { it ->
                    val isOpen = open == it.id
                    Card(Modifier.fillMaxWidth().clickable { open = if (isOpen) null else it.id }) {
                        Column(Modifier.padding(10.dp)) {
                            Text((if (isOpen) "▾ " else "▸ ") + it.q, style = MaterialTheme.typography.titleSmall)
                            if (isOpen) {
                                Text(it.a, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                                if (it.img.isNotBlank()) {
                                    val bmp = remember(it.img) { runCatching { android.graphics.BitmapFactory.decodeStream(ctx.assets.open(it.img))?.asImageBitmap() }.getOrNull() }
                                    if (bmp != null) androidx.compose.foundation.Image(bmp, contentDescription = it.q, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                }
                                if (it.src.isNotBlank()) Text("Sources : " + it.src, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}


@Composable
fun MapContainer(
    modifier: Modifier,
    onReady: (MapLibreMap, Style) -> Unit,
    onStationTap: (String) -> Unit,
    onMapTap: (LatLng) -> Unit
) {
    val ctx = LocalContext.current
    val mapView = remember { MapView(ctx) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    AndroidView(modifier = modifier, factory = {
        mapView.also { mv ->
            mv.getMapAsync { map ->
                map.uiSettings.isCompassEnabled = false
                map.cameraPosition = CameraPosition.Builder().target(LatLng(50.895, 4.32)).zoom(12.5).build()
                var usedFallback = false
                fun installLayers(st: Style) {
                    st.addSource(GeoJsonSource("grid"))
                    st.addLayer(
                        FillLayer("grid-fill", "grid").withProperties(
                            PropertyFactory.fillColor(Expression.toColor(Expression.get("color"))),
                            PropertyFactory.fillOpacity(Expression.get("op"))
                        )
                    )
                    st.addSource(GeoJsonSource("stations"))
                    st.addLayer(
                        CircleLayer("st", "stations").withProperties(
                            PropertyFactory.circleRadius(Expression.get("r")),
                            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                            PropertyFactory.circleStrokeColor("#ffffff"),
                            PropertyFactory.circleStrokeWidth(2f)
                        )
                    )
                    st.addSource(GeoJsonSource("me"))
                    st.addLayer(
                        CircleLayer("me-dot", "me").withProperties(
                            PropertyFactory.circleRadius(7f),
                            PropertyFactory.circleColor("#1a73e8"),
                            PropertyFactory.circleStrokeColor("#ffffff"),
                            PropertyFactory.circleStrokeWidth(3f)
                        )
                    )
                    onReady(map, st)
                }
                mapView.addOnDidFailLoadingMapListener {
                    if (!usedFallback) {
                        usedFallback = true
                        map.setStyle(Style.Builder().fromJson(OSM_STYLE)) { st -> installLayers(st) }
                    }
                }
                map.setStyle(Style.Builder().fromUri(OFM_STYLE_URL)) { st -> installLayers(st) }
                map.addOnMapClickListener { ll ->
                    val pt = map.projection.toScreenLocation(ll)
                    val feats = map.queryRenderedFeatures(pt, "st")
                    val id = feats.firstOrNull()?.getStringProperty("id")
                    if (id != null) onStationTap(id) else onMapTap(ll)
                    true
                }
            }
        }
    })
}


