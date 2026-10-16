
package com.noxmusic.app

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

private val Fondo = Color(0xFF09070F)
private val Panel = Color(0xFF17121F)
private val Morado = Color(0xFF9B5CFF)
private val Texto = Color(0xFFF6F1FF)
private val Secundario = Color(0xFFAAA0B8)

data class Cancion(
    val id: Long,
    val titulo: String,
    val artista: String,
    val uri: Uri
)

class MainActivity : ComponentActivity() {

    private lateinit var reproductor: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reproductor = ExoPlayer.Builder(this).build()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Morado,
                    background = Fondo,
                    surface = Panel
                )
            ) {
                NoxApp(
                    cargarMusica = { cargarCanciones() },
                    reproductor = reproductor
                )
            }
        }
    }

    private fun cargarCanciones(): List<Cancion> {
        val canciones = mutableListOf<Cancion>()
        val coleccion = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columnas = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val filtro = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            coleccion,
            columnas,
            filtro,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val tituloCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistaCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                canciones.add(
                    Cancion(
                        id = id,
                        titulo = cursor.getString(tituloCol) ?: "Canción desconocida",
                        artista = cursor.getString(artistaCol)
                            ?.takeUnless { it == "<unknown>" }
                            ?: "Artista desconocido",
                        uri = ContentUris.withAppendedId(coleccion, id)
                    )
                )
            }
        }
        return canciones
    }

    override fun onDestroy() {
        if (::reproductor.isInitialized) reproductor.release()
        super.onDestroy()
    }
}

@Composable
private fun NoxApp(
    cargarMusica: () -> List<Cancion>,
    reproductor: ExoPlayer
) {
    var canciones by remember { mutableStateOf(emptyList<Cancion>()) }
    var seccion by remember { mutableStateOf("Inicio") }
    var actual by remember { mutableStateOf<Cancion?>(null) }
    var favoritos by remember { mutableStateOf(setOf<Long>()) }
    var buscando by remember { mutableStateOf(false) }
    var consulta by remember { mutableStateOf("") }
    var permisoConcedido by remember {
        mutableStateOf(false)
    }
    var reproduciendo by remember { mutableStateOf(false) }

    val contexto = androidx.compose.ui.platform.LocalContext.current
    val permiso = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val solicitarPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        permisoConcedido = concedido
        if (concedido) canciones = cargarMusica()
    }

    LaunchedEffect(Unit) {
        permisoConcedido = ContextCompat.checkSelfPermission(
            contexto, permiso
        ) == PackageManager.PERMISSION_GRANTED

        if (permisoConcedido) canciones = cargarMusica()
        else solicitarPermiso.launch(permiso)
    }

    DisposableEffect(reproductor) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                reproduciendo = isPlaying
            }
        }
        reproductor.addListener(listener)
        onDispose { reproductor.removeListener(listener) }
    }

    val filtradas = canciones.filter {
        val coincide = it.titulo.contains(consulta, true) ||
            it.artista.contains(consulta, true)
        val seccionCoincide = when (seccion) {
            "Favoritos" -> it.id in favoritos
            else -> true
        }
        coincide && seccionCoincide
    }

    Scaffold(
        containerColor = Fondo,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                val destinos = listOf(
                    Triple("Inicio", Icons.Default.Home, "Inicio"),
                    Triple("Biblioteca", Icons.Default.LibraryMusic, "Biblioteca"),
                    Triple("Listas", Icons.Default.QueueMusic, "Listas"),
                    Triple("Favoritos", Icons.Default.Favorite, "Favoritos"),
                    Triple("Ajustes", Icons.Default.Settings, "Ajustes")
                )

                destinos.forEach { (nombre, icono, etiqueta) ->
                    NavigationBarItem(
                        selected = seccion == nombre,
                        onClick = { seccion = nombre },
                        icon = { Icon(icono, contentDescription = etiqueta) },
                        label = {
                            Text(etiqueta, fontSize = 10.sp, maxLines = 1)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Morado,
                            selectedTextColor = Morado,
                            indicatorColor = Color(0xFF302044),
                            unselectedIconColor = Secundario,
                            unselectedTextColor = Secundario
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Fondo)
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Morado, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "NOX MUSIC",
                        color = Texto,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("TU MÚSICA, TU MUNDO", color = Secundario, fontSize = 10.sp)
                }
                IconButton(onClick = { buscando = !buscando }) {
                    Icon(Icons.Default.Search, "Buscar", tint = Texto)
                }
            }

            if (buscando) {
                OutlinedTextField(
                    value = consulta,
                    onValueChange = { consulta = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar canción o artista...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            when (seccion) {
                "Inicio" -> {
                    Text("HOLA, BIENVENIDO", color = Morado, fontSize = 12.sp)
                    Text(
                        "Tu música,\ntu espacio.",
                        color = Texto,
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF251637),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TU BIBLIOTECA", color = Secundario, fontSize = 11.sp)
                            Text(
                                "${canciones.size} canciones encontradas",
                                color = Texto,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Tu colección en un solo lugar",
                                color = Secundario,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.Headphones,
                            null,
                            tint = Morado,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Tu música reciente",
                        color = Texto,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                }

                "Biblioteca" -> {
                    Text("Mi biblioteca", color = Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${canciones.size} canciones", color = Secundario)
                    Spacer(Modifier.height(12.dp))
                }

                "Listas" -> {
                    Text("Mis listas", color = Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Tus canciones, organizadas a tu manera", color = Secundario)
                    Spacer(Modifier.height(12.dp))
                }

                "Favoritos" -> {
                    Text("Mis favoritos", color = Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${favoritos.size} canciones guardadas", color = Secundario)
                    Spacer(Modifier.height(12.dp))
                }

                "Ajustes" -> {
                    Text("Ajustes", color = Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("NOX Music · Versión 1.0", color = Secundario)
                    Text("Diseñado para tu música local.", color = Secundario)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        canciones = if (permisoConcedido) cargarMusica() else emptyList()
                    }) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Actualizar biblioteca")
                    }
                }
            }

            if (seccion != "Ajustes") {
                if (!permisoConcedido) {
                    Text(
                        "Permite el acceso a tus archivos de audio para mostrar tu música.",
                        color = Secundario
                    )
                    Button(onClick = { solicitarPermiso.launch(permiso) }) {
                        Text("Dar permiso")
                    }
                } else if (filtradas.isEmpty()) {
                    Text(
                        if (seccion == "Favoritos") "Aún no tienes favoritos."
                        else "No encontramos canciones. Añade archivos de audio a tu teléfono.",
                        color = Secundario
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtradas, key = { it.id }) { cancion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        actual = cancion
                                        reproductor.setMediaItems(
                                            canciones.map { MediaItem.fromUri(it.uri) },
                                            canciones.indexOf(cancion),
                                            0L
                                        )
                                        reproductor.prepare()
                                        reproductor.play()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Panel, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        null,
                                        tint = Morado
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        cancion.titulo,
                                        color = Texto,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        cancion.artista,
                                        color = Secundario,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    favoritos = if (cancion.id in favoritos)
                                        favoritos - cancion.id
                                    else favoritos + cancion.id
                                }) {
                                    Icon(
                                        if (cancion.id in favoritos) Icons.Default.Favorite
                                        else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorito",
                                        tint = if (cancion.id in favoritos) Morado else Secundario
                                    )
                                }
                            }
                        }
                    }
                }
            }

            actual?.let { cancion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Album, null, tint = Morado, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            cancion.titulo,
                            color = Texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(cancion.artista, color = Secundario, fontSize = 11.sp)
                    }
                    IconButton(onClick = { reproductor.seekToPreviousMediaItem() }) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Texto)
                    }
                    IconButton(onClick = {
                        if (reproductor.isPlaying) reproductor.pause()
                        else reproductor.play()
                    }) {
                        Icon(
                            if (reproduciendo) Icons.Default.PauseCircle
                            else Icons.Default.PlayCircle,
                            null,
                            tint = Morado,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(onClick = { reproductor.seekToNextMediaItem() }) {
                        Icon(Icons.Default.SkipNext, null, tint = Texto)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
