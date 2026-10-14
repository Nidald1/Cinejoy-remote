package com.nedal.cinejoyremote.share

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

// Same public TMDb v3 key the PC app (sopranos_app/main.js) already embeds.
private const val TMDB_API = "https://api.themoviedb.org/3"
private const val TMDB_KEY = "8476a7ab80ad76f0936744df0430e67c"
private const val TMDB_LANG = "ar-SA"

data class TmdbRef(val type: String, val id: Long) {
    // Canonical link the PC's catalog-add understands.
    val url: String get() = "https://www.themoviedb.org/$type/$id"
}

// Finds the first themoviedb.org movie/tv link in shared text. SeriesGuide sends
// e.g. "Resident Evil https://www.themoviedb.org/movie/1423191".
private val TMDB_LINK = Regex("""themoviedb\.org/(movie|tv)/(\d+)""", RegexOption.IGNORE_CASE)

fun parseTmdbRef(text: String): TmdbRef? {
    val m = TMDB_LINK.find(text) ?: return null
    val id = m.groupValues[2].toLongOrNull() ?: return null
    return TmdbRef(m.groupValues[1].lowercase(), id)
}

data class CastMember(val name: String, val character: String, val photoUrl: String?)

data class SharedTitle(
    val ref: TmdbRef,
    val title: String,
    val year: String,
    val rating: Double?,
    val overview: String,
    val genres: List<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
    val cast: List<CastMember>,
)

// Inbox filled by MainActivity when the user shares a link into the app. The
// shell consumes it once it is signed in and on screen.
object SharedLinkInbox {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String) {
        _pending.value = text
    }

    fun consume() {
        _pending.value = null
    }
}

object TmdbFetcher {
    private val http by lazy { HttpClient(Android) { expectSuccess = true } }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(ref: TmdbRef): Result<SharedTitle> = runCatching {
        val body = http.get(
            "$TMDB_API/${ref.type}/${ref.id}?api_key=$TMDB_KEY&language=$TMDB_LANG&append_to_response=credits",
        ).bodyAsText()
        parse(ref, json.parseToJsonElement(body).jsonObject)
    }

    private fun parse(ref: TmdbRef, o: JsonObject): SharedTitle {
        val isTv = ref.type == "tv"
        val title = str(o, if (isTv) "name" else "title")
            ?: str(o, "original_title") ?: str(o, "original_name") ?: "—"
        val date = str(o, if (isTv) "first_air_date" else "release_date").orEmpty()
        val genres = (o["genres"] as? JsonArray).orEmpty().mapNotNull { str(it.jsonObject, "name") }
        val castArray = (o["credits"] as? JsonObject)?.get("cast") as? JsonArray
        val cast = castArray.orEmpty()
            .take(20)
            .map { c ->
                val obj = c.jsonObject
                CastMember(
                    name = str(obj, "name").orEmpty(),
                    character = str(obj, "character").orEmpty(),
                    photoUrl = str(obj, "profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" },
                )
            }
            .filter { it.name.isNotBlank() }
        return SharedTitle(
            ref = ref,
            title = title,
            year = date.take(4),
            rating = (o["vote_average"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 },
            overview = str(o, "overview").orEmpty(),
            genres = genres,
            posterUrl = str(o, "poster_path")?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdropUrl = str(o, "backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
            cast = cast,
        )
    }

    private fun str(o: JsonObject, key: String): String? =
        (o[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
