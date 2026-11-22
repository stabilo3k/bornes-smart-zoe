package org.borneneutre.app

import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import java.time.LocalDate

/** Ligne de choix dont toute la largeur répond au toucher (au moins 48 dp de haut) : le texte compte, pas seulement le rond. */
@Composable
fun ChoiceRow(selected: Boolean, text: String, fontSize: Int = 14, detail: String? = null, bold: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        if (detail == null) Text(text, fontSize = fontSize.sp, fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.padding(start = 8.dp))
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
private fun Choice(selected: Boolean, text: String, onClick: () -> Unit) = ChoiceRow(selected, text, 14, null, false, onClick)

/** « Qui a fait l'essai ? » : sur place (état), moi mais plus sur place (relevé réel à distance, date choisie), ou un tiers (déclaré, non testé). */
@Composable
fun EtatDialog(s: Station, myCar: String, hasPanne: Boolean, onQr: () -> Unit = {}, onDismiss: () -> Unit, onEtat: (EtatKind, Boolean, String, String) -> Unit, onPast: (Result?, String) -> Unit) {
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
                    TextButton(onClick = onQr) { QrIcon(18.dp); Text("  Lire le QR de la borne (pour la bonne borne)", fontSize = 13.sp) }
                    Text("Je suis sur place :", style = MaterialTheme.typography.labelLarge)
                    listOf(EtatKind.PANNE, EtatKind.OCCUPEE, EtatKind.FILE, EtatKind.LIBRE).forEach { k ->
                        Button(onClick = { onEtat(k, false, myCar, "sur place") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp)) { Text(k.label, fontSize = 15.sp) }
                    }
                    if (hasPanne) {
                        Text("Une panne est signalée sur cette borne :", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        OutlinedButton(onClick = { onEtat(EtatKind.REPARE, false, myCar, "sur place") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp)) { Text("✔ Panne réglée ou signalée par erreur", fontSize = 14.sp) }
                        Text("Efface l'alerte de panne pour tout le monde (les signalements plus anciens ne comptent plus). Si la panne revient, signalez-la de nouveau.", fontSize = 12.sp, color = Color(0xFF4B5563))
                    }
                    Text("Autre situation :", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { who = 1 }) { Text("J'ai essayé, mais je ne suis plus sur place") }
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
    val dial = phone ?: if (total) "0800 58 143" else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prévenir l'exploitant") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Boutons d'action en tête, pleine largeur (test10 : « Contact » se coupait en deux dans la rangée du bas).
                if (dial != null) Button(onClick = { ctx.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dial.filter { c -> c.isDigit() || c == '+' }))) }, modifier = Modifier.fillMaxWidth()) { Text("📞 Appeler l'exploitant : $dial") }
                if (op != null) OutlinedButton(onClick = { ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(op.url))) }, modifier = Modifier.fillMaxWidth()) { Text("🌐 Formulaire de contact (${op.name})") }
                Text("Rien n'est envoyé automatiquement. Copiez ou partagez ce message, ou appelez le numéro indiqué sur la borne.", fontSize = 12.sp, color = Color(0xFF4B5563))
                Text(msg, fontSize = 13.sp)
                if (phone != null) Text("Téléphone d'assistance indiqué par un utilisateur pour cette borne : $phone (à vérifier sur la borne).", fontSize = 13.sp)
                else Text("Le numéro d'assistance est écrit sur la borne ou son autocollant : notez-le avec « Compléter la fiche » pour les autres conducteurs.", fontSize = 12.sp)
                if (total && phone == null) Text("Assistance charge.brussels / TotalEnergies à Bruxelles : 0800 58 143 (vérifiez sur la borne).", fontSize = 12.sp)
                if (op != null) Text("Formulaire de contact ${op.name} : ${op.url}", fontSize = 12.sp)
                if (inBxl && s.kind != "privee") {
                    Text("Borne abîmée sur la voie publique à Bruxelles : vous pouvez aussi la signaler sur Fix My Street (plateforme régionale ; indiqué par le facilitateur régional des bornes). Rien n'est envoyé d'ici.", fontSize = 12.sp)
                    TextButton(onClick = { ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://fixmystreet.brussels/"))) }) { Text("Ouvrir Fix My Street") }
                }
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
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

/** « Urgence batterie » : les bornes les plus proches où ma voiture a déjà chargé, puis quelques bornes proches non essayées. Hors ligne. */
@Composable
fun UrgenceDialog(pos: org.maplibre.android.geometry.LatLng?, posTs: Long, now: Long, stations: List<Station>, releves: List<Releve>, car: String, broken: Set<String>, ev: List<Evidence>, priors: Map<String, Double>, ficheKw: Map<String, Double>, indiceOf: (Station) -> IndiceInfo, demo: Boolean = false, onRetry: () -> Unit = {}, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var heuristique by remember { mutableStateOf(false) }
    val items = remember(pos, stations, releves, car, broken, ev, heuristique) {
        if (pos == null) emptyList() else Urgence.nearest(pos.latitude, pos.longitude, stations, releves, car, broken, violet = { s -> s.dansLaConcession() && Essai400.near(s.lat, s.lon) != null && releves.none { r -> r.stationId == s.id } },
            indiceOf = if (heuristique) indiceOf else null)
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
                }
                if (pos == null) TextButton(onClick = onRetry) { Text("Réessayer la localisation") } else {
                    if (demo) Text("Position fictive (mode démo) : les distances partent de la Grand-Place. « Itinéraire » ouvre votre application de cartes depuis votre vraie position.", fontSize = 12.sp, color = Color(0xFF92400E), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    val ageMin = if (posTs > 0) ((now - posTs) / 60_000L).coerceAtLeast(0) else null
                    Text(if (ageMin == null) "Position : inconnue" else if (ageMin < 1) "Position : à l'instant" else "Position : il y a $ageMin min (une nouvelle position est recherchée)", fontSize = 12.sp, color = if ((ageMin ?: 99) >= 10) Color(0xFFB42318) else Color.Gray)
                }
                if (pos != null) {
                    androidx.compose.material3.FilterChip(selected = heuristique, onClick = { heuristique = !heuristique }, label = { Text(if (heuristique) "Classement par indices : activé" else "Classement par indices (heuristique)") })
                    Text(
                        if (heuristique) "Classement par indices : parmi les bornes non essayées pas beaucoup plus loin que la plus proche, indice favorable d'abord, puis aucun indice, puis indice défavorable. Les essais réels de votre voiture restent en tête. Hypothèse, pas une garantie. Voir la FAQ."
                        else "Classement normal : d'abord les bornes où votre voiture a déjà chargé (les plus proches), puis deux bornes proches pas encore essayées, la plus proche d'abord (une borne à double anneau violet peut passer devant si elle n'est pas beaucoup plus loin). Une panne signalée repousse la borne. Les indices (autocollants, listes) ne comptent pas ici : touchez le bouton pour les prendre en compte. Voir la FAQ.",
                        fontSize = 11.sp, color = Color.Gray)
                }
                if (pos != null && items.isEmpty()) Text("Aucune borne connue à proximité pour $car. Agrandissez la carte ou contactez l'assistance de votre constructeur.", fontSize = 13.sp)
                items.forEach { it ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${Urgence.distLabel(it.distM)} · ${it.station.name}", style = MaterialTheme.typography.titleMedium)
                            if (it.broken) Text("⚠ Panne signalée par un utilisateur (non vérifiée) : à tenter en dernier", fontSize = 12.sp, color = Color(0xFFB42318))
                            if (it.dc) Text("Borne rapide en courant continu : il faut la prise CCS (option, pas sur toutes les Zoé).", fontSize = 12.sp, color = Color(0xFFB45309))
                            if (heuristique && !it.tried) it.indice?.let { ind -> Text("${ind.niveau}", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                            Text(if (it.tried) it.status.label else if (it.violet) "À essayer, non vérifiée : annoncée 400 V par la Région (hypothèse)" else "Pas encore essayée avec $car : à tenter", fontSize = 12.sp, color = Color(android.graphics.Color.parseColor(it.status.color)))
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
                Text("Sticker vu sur la borne : utilisez le bouton « Sticker sur la borne » de la fiche.", fontSize = 12.sp, color = Color(0xFF4B5563))
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
fun PastilleDialog(s: Station, onDismiss: () -> Unit, onFaq: () -> Unit = {}, onSave: (String, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember { runCatching { android.graphics.BitmapFactory.decodeStream(ctx.assets.open("faq-pastilles-v3.png"))?.asImageBitmap() }.getOrNull() }
    var code by remember { mutableStateOf("") }
    var tested by remember { mutableStateOf<Boolean?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    if (zoomed && bmp != null) ImageZoomDialog(bmp, "Exemples de stickers vus sur des bornes") { zoomed = false }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sticker vu sur la borne", maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bmp?.let {
                    androidx.compose.foundation.Image(it, contentDescription = "Exemples de stickers vus sur des bornes (illustration du projet). Toucher pour agrandir.", modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Agrandir l'illustration", role = androidx.compose.ui.semantics.Role.Button) { zoomed = true })
                    TextButton(onClick = { zoomed = true }) { Text("🔍 Toucher l'illustration pour l'agrandir", fontSize = 13.sp) }
                }
                Text(s.name, fontSize = 13.sp)
                TextButton(onClick = onFaq) { Text("Qu'est-ce que le sticker ?", fontSize = 13.sp) }
                Text("Sticker vu sur la borne", style = MaterialTheme.typography.labelLarge)
                PASTILLES.forEach { (c, lib) ->
                    val col = when (c) { "verte" -> Triple(Color(0xFF1B7A3A), Color(0xFFD8F0DF), true); "bleue" -> Triple(Color(0xFF1D4ED8), Color(0xFFDCE8FB), true); "blanche" -> Triple(Color(0xFF4B5563), Color.White, true); else -> null }
                    if (col == null) ChoiceRow(code == c, lib, 13, bold = c == "aucune") { code = c }
                    else {
                        val sel = code == c
                        androidx.compose.material3.Surface(color = if (sel || c == "blanche") col.second else col.second.copy(alpha = 0.55f), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(if (sel) 4.dp else 2.dp, col.first), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected = sel, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { code = c }).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(selected = sel, onClick = null, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = col.first, unselectedColor = col.first))
                                Text(lib, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = col.first, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                Text("Avez-vous branché une voiture sur cette borne ?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                ChoiceRow(tested == false, "Non : simple constat visuel de l'autocollant", 13) { tested = false }
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

/** Trois écrans d'explication, affichés une seule fois après le choix de la voiture au premier lancement. */
@Composable
fun IntroDialog(onDone: () -> Unit) {
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val titles = listOf("À quoi sert l'application", "Comment signaler", "Vos données")
    val texts = listOf(
        "Certaines bornes belges refusent de charger les Smart EQ et les Renault Zoé. L'application rassemble les essais des conducteurs (« ça charge » / « ne charge pas ») pour montrer sur une carte où ça a déjà marché. Les anneaux violet et gris sont des hypothèses, pas des mesures.",
        "Sur place, après un vrai essai, touchez « Ça charge » ou « Ne charge pas ». Vous pouvez aussi noter le sticker collé sur la borne, une panne, ou lire le QR. Le bouton « 🔋 Urgence » trouve les bornes proches quand la batterie est faible.",
        "Les relevés sont anonymes : ni nom, ni plaque, ni trajet. Votre position n'est pas envoyée, sauf celle d'une borne publique que vous ajoutez vous-même. Les bornes privées ne sont jamais envoyées. Détail dans la FAQ. Le code est libre (GPL)."
    )
    AlertDialog(onDismissRequest = {}, title = { Text("${titles[page]} (${page + 1}/3)") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(texts[page], fontSize = 14.sp) } },
        confirmButton = { TextButton(onClick = { if (page < 2) page++ else onDone() }) { Text(if (page < 2) "Suivant" else "Commencer") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Passer") } })
}
