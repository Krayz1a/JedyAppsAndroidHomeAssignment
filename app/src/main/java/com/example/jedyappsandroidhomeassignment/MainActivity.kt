@file:OptIn(kotlinx.serialization.InternalSerializationApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalMaterial3Api::class)

package com.example.jedyappsandroidhomeassignment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import coil.compose.SubcomposeAsyncImage
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.navigation.NavController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

@Serializable
data class Movie(
    val Title: String,
    val Poster: String? = null,
    val Type: String? = null,
    val imdbID: String
)
@Serializable
data class APIResponse(
    val Search: List<Movie>? = null,
    val Response: String? = null,
    val Error: String? = null
)
suspend fun getMovies(title: String): List<Movie> {
    val response: APIResponse = client.get("https://www.omdbapi.com/") {
        parameter("apikey", "3e836853")
        parameter("s", title)
        parameter("type", "movie")
    }.body()
    return if (response.Response == "True") response.Search.orEmpty() else emptyList()
}
suspend fun getMovie(id: String): Movie?{
    return client.get("https://www.omdbapi.com/") {
        parameter("apikey", "3e836853")
        parameter("i", id)
    }.body()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navigator = rememberNavController()
            NavHost(navController = navigator, startDestination = "main") {
                composable("main") {
                    MainScreen(navigator = navigator)
                }
                composable("favorites") {
                    FavoritesScreen(navigator = navigator)
                }
                composable("details/{ID}") {
                    DetailScreen(ID = it.arguments?.getString("ID")!!, navigator = navigator)
                }
            }
        }
    }
}


@Composable
fun MainScreen(navigator: NavController) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { Db.get(context).dao() }
    val scope = rememberCoroutineScope()
    val favorites by dao.getAll().collectAsState(emptyList())

    var value by remember { mutableStateOf<String>("") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var loading by remember { mutableStateOf<Boolean>(false) }
    var error by remember { mutableStateOf<String?>(null) }


    fun submit() {
        value = value.trim()
        if (!value.isEmpty()) {
            scope.launch {
                loading = true
                error = null
                movies = try {
                    val list = getMovies(value)
                    if (list.isEmpty()) {
                        error =
                            "No results for \"$value\" or too many results. Try again."
                    }
                    list.distinctBy { it.imdbID }
                } catch (e: Exception) {
                    error = "Error: ${e.message}"
                    emptyList()
                }
                loading = false
                value = ""
            }
        }
    }


    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navigator.navigate("favorites")
                }
            ) {
                Icon(Icons.Filled.Favorite,"Favorites")
            }
        }
    ) { inner ->
        Column(modifier = Modifier.padding(inner).padding(horizontal = 16.dp)) {
            TextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Search movies") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit()
                    focusManager.clearFocus()})
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (loading) CircularProgressIndicator()

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn {
                items(
                    items = movies,
                    key = { it.imdbID }
                ) { movie ->
                    val isFavorite = favorites.any { it.imdbID == movie.imdbID }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { navigator.navigate("details/${movie.imdbID}") }
                        ) { Text(movie.Title) }


                        if (!isFavorite) {
                            Button(onClick = {
                                scope.launch {
                                    dao.upsert(
                                        FavoriteMovie(
                                            imdbID = movie.imdbID,
                                            title = movie.Title,
                                            poster = movie.Poster,
                                            type = movie.Type
                                        )
                                    )
                                }
                            }) {
                                Icon(Icons.Filled.FavoriteBorder, null)
                                Text("Add")
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        dao.deleteById(movie.imdbID)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Icon(Icons.Filled.Check, "Added")
                                Spacer(Modifier.width(4.dp))
                                Text("Added")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(navigator: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { Db.get(context).dao() }
    val favorites by dao.getAll().collectAsState(emptyList())

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = {navigator.popBackStack()}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "return")
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(Modifier.padding(inner)) {
            items(favorites, key = { it.imdbID }) { fav ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fav.title, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        scope.launch { dao.deleteById(fav.imdbID) }
                    }) {
                        Icon(Icons.Filled.Delete, null)
                        Text("Remove")

                    }
                }
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }
    }
}

@Composable
fun DetailScreen(ID: String, navigator: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { Db.get(context).dao() }
    val scope = rememberCoroutineScope()
    val favorites by dao.getAll().collectAsState(emptyList())

    var movie by remember { mutableStateOf<Movie?>(null) }
    var loading by remember { mutableStateOf<Boolean>(false) }
    var error by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(ID) {
        loading = true
        try {
            val result = getMovie(ID)
            if (result != null) {
                movie = result
            } else {
                error = "Unknown error"
            }
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(movie?.Title ?: "Loading") },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "return")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ){
            if (loading) CircularProgressIndicator()

            else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }


            else if (movie != null) {
                Text(
                    text = movie!!.Title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                val poster = movie!!.Poster
                if (!poster.isNullOrBlank() && poster != "N/A") {
                    SubcomposeAsyncImage(
                        model = poster.replaceFirst("http://", "https://"),
                        contentDescription = "Poster for ${movie!!.Title}",
                        modifier = Modifier.height(600.dp),

                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        },

                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Poster not found")
                            }
                        }
                    )
                }  else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { Text("Poster not found") }
                }

                Spacer(Modifier.height(10.dp))

                val isFavorite = favorites.any { it.imdbID == movie!!.imdbID }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Type: ${movie!!.Type ?: "Unknown"}")
                    if (!isFavorite) {
                        Button(onClick = {
                            scope.launch {
                                dao.upsert(
                                    FavoriteMovie(
                                        imdbID = movie!!.imdbID,
                                        title = movie!!.Title,
                                        poster = movie!!.Poster,
                                        type = movie!!.Type
                                    )
                                )
                            }
                        }) {
                            Icon(Icons.Filled.FavoriteBorder, null)
                            Text("Add")
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    dao.deleteById(movie!!.imdbID)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Filled.Check, "Added")
                            Spacer(Modifier.width(4.dp))
                            Text("Added")
                        }
                    }
                }
            }
        }
    }
}