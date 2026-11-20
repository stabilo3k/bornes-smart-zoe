package org.borneneutre.app

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import java.time.LocalDate

/** Ligne de choix dont toute la largeur répond au toucher (au moins 48 dp de haut) : le texte compte, pas seulement le rond. */
@Composable
fun ChoiceRow(selected: Boolean, text: String, fontSize: Int = 14, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        if (detail == null) Text(text, fontSize = fontSize.sp, modifier = Modifier.padding(start = 8.dp))
        else Column(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Text(text, fontSize = fontSize.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(detail, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF4B5563))
        }
    }
}

/** Case à cocher avec son texte : toute la ligne se touche (au moins 48 dp de haut). */
@Composable
fun CheckRow(checked: Boolean, text: String, fontSize: Int = 13, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
        Text(text, fontSize = fontSize.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Choice(selected: Boolean, text: String, onClick: () -> Unit) = ChoiceRow(selected, text, 14, null, onClick)

/** « Qui a fait l'essai ? » : sur place (état), moi mais plus sur place (relevé réel à distance, date choisie), ou un tiers (déclaré, non testé). */
@Composable
fun EtatDialog(s: Station, myCar: String, onDismiss: () -> Unit, onEtat: (EtatKind, Boolean, String, String) -> Unit, onPast: (Result?, String) -> Unit) {
    var who by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf<EtatKind?>(null) }
    var pick by remember { mutableStateOf(0) } // 1 = ça charge, 2 = ne charge pas, 3 = en panne
    var whenIdx by remember { mutableStateOf(0) }
    var source by remember { mutableStateOf("Conducteur rencontré") }
    var car by remember { mutableStateOf("") }
    val sources = listOf("Conducteur rencontré", "Voyant ou écran de la borne", "Autre")
    val today = LocalDate.now()
    val whens = listOf("Aujourd'hui" to today, "Hier" to today.minusDays(1), "Avant-hier" to today.minusDays(2))
    val ready = when (who) { 0 -> kind != null; 1 -> pick != 0; else -> kind != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.name, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (who == 0) {
                    Text("Je suis sur place :", style = MaterialTheme.typography.labelLarge)
                    listOf(EtatKind.PANNE, EtatKind.OCCUPEE, EtatKind.FILE, EtatKind.LIBRE).forEach { k ->
                        Button(onClick = { onEtat(k, false, myCar, "sur place") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp)) { Text(k.label, fontSize = 15.sp) }
                    }
                    Text("Autre situation :", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { who = 1 }) { Text("J'ai essayé, mais je ne suis plus sur place") }
                    TextButton(onClick = { who = 2; kind = null }) { Text("Quelqu'un d'autre me l'a dit") }
                } else {
                    TextButton(onClick = { who = 0; kind = null }) { Text("← Retour") }
                    Text(if (who == 1) "Résultat de mon essai" else "État de la borne", style = MaterialTheme.typography.labelLarge)
                }
                when (who) {
                    0 -> {}
                    1 -> {
                        Choice(pick == 1, "✅ Ça charge") { pick = 1 }
                        Choice(pick == 2, "❌ Ne charge pas (la borne démarre mais refuse ma voiture)") { pick = 2 }
                        Choice(pick == 3, "⚠ En panne (la borne ne démarre pas, voyant d'erreur, éteinte)") { pick = 3 }
                        Text("Quand ?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        whens.forEachIndexed { i, w -> Choice(whenIdx == i, w.first) { whenIdx = i } }
                        Text("Un essai de charge est enregistré comme un vrai relevé « à distance » avec ma voiture ($myCar) ; une panne comme un état de borne.", fontSize = 12.sp, color = Color(0xFF4B5563))
                    }
                    else -> {
                        listOf(EtatKind.MARCHE, EtatKind.NEMARCHEPAS, EtatKind.PANNE).forEach { k -> Choice(kind == k, k.label) { kind = k } }
                        Text("Source", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        sources.forEach { x -> Choice(source == x, x) { source = x } }
                        Text("Voiture de la personne", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        (CARS + "Autre / je ne sais pas").forEach { c -> Choice(car == (if (c.startsWith("Autre")) "" else c) && (car.isNotEmpty() || c.startsWith("Autre")), c) { car = if (c.startsWith("Autre")) "" else c } }
                        Text("Vous n'avez pas testé cette borne vous-même. Ce signalement sera marqué « déclaré, non vérifié » et ne change pas le verdict.", fontSize = 12.sp, color = Color(0xFFB45309), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (who != 0) TextButton(enabled = ready, onClick = {
                when (who) {
                    0 -> onEtat(kind!!, false, myCar, "sur place")
                    1 -> onPast(when (pick) { 1 -> Result.OK; 2 -> Result.REFUS; else -> null }, whens[whenIdx].second.toString())
                    else -> onEtat(kind!!, true, car, source)
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** « Prévenir l'exploitant » : message prêt à copier ou partager ; rien n'est envoyé automatiquement. */
@Composable
fun WarnDialog(s: Station, phone: String?, car: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val msg = remember(s.id) {
        "Bonjour,\nla borne « ${s.name} »" + (if (s.operator.isNotBlank()) " (${s.operator})" else "") + " (position ${String.format(java.util.Locale.US, "%.5f, %.5f", s.lat, s.lon)}) " +
            "Ma voiture ($car) n'a pas pu charger sur cette borne le ${LocalDate.now()}. Pourriez-vous m'indiquer si la borne fonctionne et à quel type de raccordement elle est alimentée (3×400 V + neutre ou 3×230 V) ? Merci.\n— Message préparé avec l'application Bornes Smart et Zoé (aucune donnée personnelle)."
    }
    // Le 0800 58 143 est l'assistance de charge.brussels / TotalEnergies à Bruxelles : jamais proposé ailleurs.
    val inBxl = s.lat in 50.76..50.92 && s.lon in 4.24..4.50
    val total = inBxl && (s.operator.contains("total", ignoreCase = true) || s.operator.contains("charge.brussels", ignoreCase = true))
    val op = Operators.forOperator(s.operator)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prévenir l'exploitant") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Rien n'est envoyé automatiquement. Copiez ou partagez ce message, ou appelez le numéro indiqué sur la borne.", fontSize = 12.sp, color = Color(0xFF4B5563))
                Text(msg, fontSize = 13.sp)
                if (phone != null) Text("Téléphone d'assistance indiqué par un utilisateur pour cette borne : $phone (à vérifier sur la borne).", fontSize = 13.sp)
                else Text("Le numéro d'assistance est écrit sur la borne ou son autocollant : notez-le avec « Compléter la fiche » pour les autres conducteurs.", fontSize = 12.sp)
                if (total && phone == null) Text("Assistance charge.brussels / TotalEnergies à Bruxelles : 0800 58 143 (vérifiez sur la borne).", fontSize = 12.sp)
                if (op != null) Text("Formulaire de contact ${op.name} : ${op.url}", fontSize = 12.sp)
                Text("À donner en appelant : le lieu, l'identifiant de la borne (étiquette), l'heure et le message d'erreur affiché.", fontSize = 12.sp, color = Color(0xFF4B5563))
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("message", msg))
                    Toast.makeText(ctx, "Message copié", Toast.LENGTH_SHORT).show()
                }) { Text("Copier") }
                TextButton(onClick = { ctx.safeStart(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Partager")) }) { Text("Partager") }
                val dial = phone ?: if (total) "0800 58 143" else null
                if (dial != null) TextButton(onClick = { ctx.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dial.filter { c -> c.isDigit() || c == '+' }))) }) { Text("Appeler") }
                if (op != null) TextButton(onClick = { ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(op.url))) }) { Text("Contact") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

/** « Urgence batterie » : les bornes les plus proches où ma voiture a déjà chargé, puis quelques bornes proches non essayées. Hors ligne. */
@Composable
fun UrgenceDialog(pos: org.maplibre.android.geometry.LatLng?, posTs: Long, now: Long, stations: List<Station>, releves: List<Releve>, car: String, broken: Set<String>, ev: List<Evidence>, priors: Map<String, Double>, ficheKw: Map<String, Double>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember(pos, stations, releves, car, broken, ev) {
        if (pos == null) emptyList() else Urgence.nearest(pos.latitude, pos.longitude, stations, releves, car, broken, score = { s -> Estimation.estimate(s.lat, s.lon, ev, priors[s.id]).p })
    }
    val obsKw = remember(releves) { releves.filter { it.kw != null }.groupBy { it.stationId }.mapValues { e -> e.value.maxOf { it.kw!! } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔋 Urgence batterie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pos == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Recherche de votre position… (autorisez la localisation si on vous le demande ; la liste apparaît dès que la position est trouvée)", fontSize = 13.sp)
                } else {
                    val ageMin = if (posTs > 0) ((now - posTs) / 60_000L).coerceAtLeast(0) else null
                    Text(if (ageMin == null) "Position : inconnue" else if (ageMin < 1) "Position : à l'instant" else "Position : il y a $ageMin min (une nouvelle position est recherchée)", fontSize = 12.sp, color = if ((ageMin ?: 99) >= 10) Color(0xFFB42318) else Color.Gray)
                }
                if (pos != null && items.isEmpty()) Text("Aucune borne connue à proximité pour $car. Agrandissez la carte ou contactez l'assistance de votre constructeur.", fontSize = 13.sp)
                items.forEach { it ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${Urgence.distLabel(it.distM)} · ${it.station.name}", style = MaterialTheme.typography.titleMedium)
                            if (it.broken) Text("⚠ Panne signalée par un utilisateur (non vérifiée) : à tenter en dernier", fontSize = 12.sp, color = Color(0xFFB42318))
                            if (it.dc) Text("Borne rapide en courant continu : il faut la prise CCS (option, pas sur toutes les Zoé).", fontSize = 12.sp, color = Color(0xFFB45309))
                            Text(if (it.tried) it.status.label else "Pas encore essayée avec $car : à tenter", fontSize = 12.sp, color = Color(android.graphics.Color.parseColor(it.status.color)))
                                                        Text(StationInfo.powerLabel(it.station, ficheKw[it.station.id], obsKw[it.station.id]) + (StationInfo.accessLabel(it.station)?.let { a -> " · 🔒 $a" } ?: ""), fontSize = 12.sp)
                            StationInfo.openingHours(it.station)?.let { h -> Text("🕒 $h (d'après la source ; à confirmer)", fontSize = 12.sp) }
                            Button(onClick = { ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${it.station.lat},${it.station.lon}?q=${it.station.lat},${it.station.lon}(${Uri.encode(it.station.name)})"))) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Itinéraire", fontSize = 16.sp) }
                        }
                    }
                }
                Text("Aucune garantie : une borne qui a chargé une fois peut refuser ensuite. Si la batterie est presque vide, appelez l'assistance de votre constructeur.", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}


@Composable
private fun field(text: String, onChange: (String) -> Unit, label: String) =
    androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { onChange(it.take(6)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))

/** Compléter la fiche d'une borne : nombre de bornes à cet endroit, prises par borne, puissance. Tout est facultatif ; partagé avec la communauté. */
@Composable
fun FicheDialog(s: Station, current: Fiche?, osmPoints: Int?, onDismiss: () -> Unit, onSave: (Int?, Int?, Double?, String?, String?) -> Unit) {
    var contact by remember { mutableStateOf(current?.contact ?: "") }
    var points by remember { mutableStateOf(current?.points?.toString() ?: "") }
    var plugs by remember { mutableStateOf(current?.plugs?.toString() ?: "") }
    var kw by remember { mutableStateOf(current?.kw?.toString()?.removeSuffix(".0") ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compléter la fiche", maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.name, fontSize = 13.sp)
                Text("Combien de bornes (boîtiers) à cet endroit, et combien de prises par borne ? Exemple : Lidl = 2 bornes, 1 prise par borne ; Colruyt = 2 bornes, 2 prises par borne.", fontSize = 12.sp, color = Color(0xFF4B5563))
                if (osmPoints != null) Text("OpenStreetMap indique $osmPoints point(s) de charge ici.", fontSize = 12.sp)
                field(points, { points = it }, "Nombre de bornes")
                field(plugs, { plugs = it }, "Prises par borne")
                field(kw, { kw = it }, "Puissance de la borne, en kW (si affichée)")
                Text("Pastille vue sur la borne : utilisez le bouton rond « Pastille » de la fiche.", fontSize = 12.sp, color = Color(0xFF4B5563))
                androidx.compose.material3.OutlinedTextField(value = contact, onValueChange = { contact = it.take(20) }, label = { Text("Téléphone d'assistance (écrit sur la borne)") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                Text("Le numéro d'assistance figure sur la borne ou son autocollant : il servira à prévenir l'exploitant d'une panne. Laissez vide ce que vous ne savez pas. Ces informations sont partagées anonymement et ne changent pas le verdict de compatibilité.", fontSize = 12.sp, color = Color(0xFF4B5563))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Fiches.parseCount(points, 30), Fiches.parseCount(plugs, 8), Etats.parseKw(kw), Fiches.parsePhone(contact), current?.pastille) },
                enabled = Fiches.parseCount(points, 30) != null || Fiches.parseCount(plugs, 8) != null || Etats.parseKw(kw) != null || Fiches.parsePhone(contact) != null) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Pastille vue sur la borne : constat visuel, distinct d'un essai de charge. Les deux réponses sont obligatoires. */
@Composable
fun PastilleDialog(s: Station, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var code by remember { mutableStateOf("") }
    var tested by remember { mutableStateOf<Boolean?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pastille vue sur la borne", maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.name, fontSize = 13.sp)
                Text("Quelle pastille de compatibilité est collée sur la borne ?", style = MaterialTheme.typography.labelLarge)
                PASTILLES.forEach { (c, lib) -> ChoiceRow(code == c, lib, 13) { code = c } }
                Text("Avez-vous branché une voiture sur cette borne ?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                ChoiceRow(tested == false, "Non : simple constat visuel", 13) { tested = false }
                ChoiceRow(tested == true, "Oui, j'ai branché une voiture (enregistrez aussi « Ça charge » ou « Ne charge pas » pour le résultat)", 13) { tested = true }
                Text("Un constat visuel ne prouve pas que la borne est compatible : il est partagé anonymement, séparé des relevés, et ne change pas le verdict. Aucune position n'est envoyée.", fontSize = 12.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(code, tested == true) }, enabled = code.isNotBlank() && tested != null) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Ajouter une borne absente de la carte à l'endroit touché (partagée avec la communauté, avec sa fiche). */
@Composable
fun NewStationDialog(onDismiss: () -> Unit, onSave: (String, Int?, Int?, Double?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("") }
    var plugs by remember { mutableStateOf("") }
    var kw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une borne ici") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pour une borne publique absente de la carte (ex. un parking de commune, un supermarché). Elle sera visible par tous. Pas de borne privée ou à domicile.", fontSize = 12.sp, color = Color(0xFF4B5563))
                androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Nom ou adresse (ex. Albert Heijn Wemmel)") }, singleLine = true)
                field(points, { points = it }, "Nombre de bornes (facultatif)")
                field(plugs, { plugs = it }, "Prises par borne (facultatif)")
                field(kw, { kw = it }, "Puissance en kW (facultatif)")
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), Fiches.parseCount(points, 30), Fiches.parseCount(plugs, 8), Etats.parseKw(kw)) }) { Text("Ajouter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Renommer une borne ajoutée par l'utilisateur (adresse plutôt que « Nouvelle borne »). */
@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(if (current == "Nouvelle borne") "" else current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer la borne") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mettez l'adresse, comme pour les autres bornes (ex. Avenue Docteur Folley 12, Wemmel).", fontSize = 12.sp, color = Color(0xFF4B5563))
                androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Adresse ou nom") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Illustration en grand : pincer pour zoomer, glisser pour déplacer, toucher « Fermer » pour revenir. */
@Composable
fun ImageZoomDialog(bmp: androidx.compose.ui.graphics.ImageBitmap, title: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
            androidx.compose.foundation.Image(
                bmp, contentDescription = title,
                modifier = Modifier.fillMaxSize()
                    .androidx_pointer { zoom, pan -> scale = (scale * zoom).coerceIn(1f, 6f); offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                TextButton(onClick = { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero }) { Text("Recentrer", color = Color.White) }
                TextButton(onClick = onDismiss) { Text("Fermer ✕", color = Color.White) }
            }
            Text("Pincez pour agrandir", color = Color(0xFFBBBBBB), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

private fun Modifier.androidx_pointer(onGesture: (Float, androidx.compose.ui.geometry.Offset) -> Unit): Modifier =
    this.pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> onGesture(zoom, pan) } }

/** Lance une intention sans planter si aucune application ne la gère (cartes, téléphone, navigateur absents). */
fun android.content.Context.safeStart(i: android.content.Intent) {
    try { startActivity(i) } catch (e: Exception) { android.widget.Toast.makeText(this, "Aucune application disponible pour cette action.", android.widget.Toast.LENGTH_LONG).show() }
}


@Composable
fun RaccordDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var region by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Bruxelles") }
    var gid by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("sibelga") }
    val options = Raccord.deRegion(region)
    val g = options.firstOrNull { it.id == gid } ?: options.first()
    val dark = androidx.compose.ui.graphics.Color(0xFF1F2937)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Préparer ma demande", style = androidx.compose.material3.MaterialTheme.typography.titleMedium) },
        text = {
            androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.weight(1f, fill = false).heightIn(min = 340.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                androidx.compose.material3.Text("Hors ligne : l'application ne remplit rien, n'envoie rien et ne garantit rien. Le gestionnaire peut refuser, demander des travaux ou un devis.", fontSize = 13.sp)
                androidx.compose.material3.Text("Où habitez-vous ?", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = dark, modifier = androidx.compose.ui.Modifier.padding(top = 6.dp))
                Raccord.REGIONS.forEach { r -> ChoiceRow(region == r, r, 14) { region = r; gid = Raccord.deRegion(r).first().id } }
                if (options.size > 1) {
                    androidx.compose.material3.Text("Votre gestionnaire (recherche par code postal sur le site de la CWaPE)", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = dark, modifier = androidx.compose.ui.Modifier.padding(top = 6.dp))
                    options.forEach { o -> ChoiceRow(g.id == o.id, o.nom, 14) { gid = o.id } }
                }
                androidx.compose.material3.Text(g.nom, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = dark, modifier = androidx.compose.ui.Modifier.padding(top = 10.dp))
                androidx.compose.material3.Text(g.demarche, fontSize = 13.sp)
                androidx.compose.material3.Text("À rassembler", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = dark, modifier = androidx.compose.ui.Modifier.padding(top = 6.dp))
                g.pieces.forEach { androidx.compose.material3.Text("• $it", fontSize = 13.sp) }
                androidx.compose.material3.Text("À savoir", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = dark, modifier = androidx.compose.ui.Modifier.padding(top = 6.dp))
                g.aSavoir.forEach { androidx.compose.material3.Text("• $it", fontSize = 13.sp) }
                androidx.compose.material3.Text("Source : ${g.source}", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF4B5563))
            }
            androidx.compose.material3.HorizontalDivider()
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.TextButton(onClick = {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("demande", Raccord.modele(g)))
                        android.widget.Toast.makeText(ctx, "Texte copié : complétez les points …", android.widget.Toast.LENGTH_LONG).show()
                    }) { androidx.compose.material3.Text("Copier un texte à adapter") }
                    if (g.url.startsWith("https://")) androidx.compose.material3.TextButton(onClick = {
                        ctx.safeStart(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(g.url)))
                    }) { androidx.compose.material3.Text("Ouvrir la page") }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Fermer") } }
    )
}
