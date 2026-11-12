/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.network.HttpService
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.CatalogBundle
import app.morphe.gui.data.model.CatalogStoreApp
import app.morphe.gui.data.model.PatchSourceCatalog
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Repository for fetching and filtering community patch sources from the online catalog.
 */
class PatchSourceCatalogRepository(
    httpClient: HttpClient,
) {
    private val http = HttpService(httpClient)
    private val mutex = Mutex()
    private var cachedCatalog: PatchSourceCatalog? = null

    companion object {
        const val CATALOG_URL = "https://morphe-patches.software/data/catalog.json"
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
    }

    /**
     * Fetches or returns cached catalog data.
     */
    suspend fun getCatalog(forceRefresh: Boolean = false): PatchSourceCatalog = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!forceRefresh && cachedCatalog != null) {
                return@withContext cachedCatalog!!
            }
            try {
                val catalog: PatchSourceCatalog = http.request(CATALOG_URL) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                        append(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                        append(HttpHeaders.UserAgent, USER_AGENT)
                        append(HttpHeaders.Referrer, "https://morphe-patches.software/")
                        append("sec-ch-ua", "\"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"156\", \"Google Chrome\";v=\"156\"")
                        append("sec-ch-ua-mobile", "?0")
                        append("sec-ch-ua-platform", "\"macOS\"")
                        append("sec-fetch-dest", "empty")
                        append("sec-fetch-mode", "cors")
                        append("sec-fetch-site", "same-origin")
                    }
                }
                cachedCatalog = catalog
                catalog
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error("Failed to load patch source catalog: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Filters bundles matching all terms in [query] across name, author, repo, apps, and patches.
     */
    fun filterBundles(
        bundles: List<CatalogBundle>,
        store: Map<String, CatalogStoreApp>,
        query: String,
    ): List<CatalogBundle> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            return bundles.sortedByDescending { it.stars }
        }
        val keywords = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        return bundles.filter { bundle ->
            val appNames = bundle.targetApps.mapNotNull { store[it]?.name?.lowercase() }
            val patchNames = bundle.patches.map { it.name.lowercase() }
            val targetApps = bundle.targetApps.map { it.lowercase() }
            val name = bundle.name.lowercase()
            val repo = bundle.repo.lowercase()
            val author = (bundle.author ?: "").lowercase()
            val desc = (bundle.repoDescription ?: "").lowercase()

            keywords.all { kw ->
                name.contains(kw) ||
                repo.contains(kw) ||
                author.contains(kw) ||
                desc.contains(kw) ||
                targetApps.any { it.contains(kw) } ||
                appNames.any { it.contains(kw) } ||
                patchNames.any { it.contains(kw) }
            }
        }.sortedByDescending { bundle ->
            var score = bundle.stars
            val name = bundle.name.lowercase()
            val repo = bundle.repo.lowercase()
            if (name == trimmed) score += 10000
            else if (name.startsWith(trimmed)) score += 5000
            else if (repo.contains(trimmed)) score += 2000
            score
        }
    }
}
