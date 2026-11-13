/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.network.createHttpClient
import app.morphe.gui.data.model.CatalogBundle
import app.morphe.gui.data.model.CatalogPatch
import app.morphe.gui.data.model.CatalogStoreApp
import app.morphe.gui.data.model.PatchSourceCatalog
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchSourceCatalogRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sampleStore = mapOf(
        "com.google.android.youtube" to CatalogStoreApp(name = "YouTube"),
        "com.reddit.frontpage" to CatalogStoreApp(name = "Reddit"),
        "com.spotify.music" to CatalogStoreApp(name = "Spotify"),
    )

    private val sampleBundles = listOf(
        CatalogBundle(
            source = "github",
            repo = "MorpheApp/morphe-patches",
            name = "Morphe Patches",
            author = "MorpheApp",
            repoDescription = "Official Morphe patch bundle for YouTube and other apps",
            patchCount = 45,
            targetApps = listOf("com.google.android.youtube"),
            stars = 120,
            patches = listOf(CatalogPatch(name = "SponsorBlock"), CatalogPatch(name = "Return YouTube Dislike")),
        ),
        CatalogBundle(
            source = "github",
            repo = "0xRJDev/shikify",
            name = "Shikify",
            author = "0xRJDev",
            repoDescription = "Material 3 and Material You patches for Reddit",
            patchCount = 25,
            targetApps = listOf("com.reddit.frontpage"),
            stars = 15,
            patches = listOf(CatalogPatch(name = "Material You"), CatalogPatch(name = "Swipe actions")),
        ),
        CatalogBundle(
            source = "gitlab",
            repo = "custom-user/music-patches",
            name = "Music Patches",
            author = "custom-user",
            repoDescription = "Patches for Spotify music player",
            patchCount = 8,
            targetApps = listOf("com.spotify.music"),
            stars = 50,
            patches = listOf(CatalogPatch(name = "Remove ads")),
        ),
    )

    private val dummyRepo = PatchSourceCatalogRepository(createHttpClient(json))

    private val socialCatalog = PatchSourceCatalog(
        store = mapOf(
            "com.instagram.android" to CatalogStoreApp(name = "Instagram"),
            "com.facebook.katana" to CatalogStoreApp(name = "Facebook"),
            "com.facebook.lite" to CatalogStoreApp(name = "Facebook"),
        ),
        bundles = listOf(
            CatalogBundle(
                repo = "owner/social",
                name = "Social patches",
                targetApps = listOf("com.instagram.android", "com.facebook.katana", "com.instagram.android"),
                stars = 10,
                patches = listOf(CatalogPatch(name = "Remove ads")),
            ),
            CatalogBundle(repo = "owner/photos", targetApps = listOf("com.instagram.android"), stars = 20),
            CatalogBundle(repo = "owner/lite", targetApps = listOf("com.facebook.lite")),
            CatalogBundle(repo = "owner/unknown", targetApps = listOf("com.unknown.app")),
            CatalogBundle(repo = "owner/general"),
        ),
    )

    @Test
    fun `groups multi-app sources once per package and sorts sources by stars`() {
        val groups = dummyRepo.filterAppGroups(socialCatalog, "")
        assertEquals("Instagram", groups.first().name)
        assertEquals(listOf("owner/photos", "owner/social"), groups.first().bundles.map { it.repo })
        assertEquals(2, groups.count { it.name == "Facebook" })
        assertEquals("com.unknown.app", groups.first { it.packageName == "com.unknown.app" }.name)
        assertEquals(listOf("owner/general"), groups.last().bundles.map { it.repo })
        assertEquals(null, groups.last().packageName)
    }

    @Test
    fun `app search excludes sibling apps supported by the same source`() {
        val groups = dummyRepo.filterAppGroups(socialCatalog, " INSTAGRAM ")
        assertEquals(listOf("com.instagram.android"), groups.map { it.packageName })
        assertEquals(2, groups.single().bundles.size)
    }

    @Test
    fun `app search combines app name with patch or source terms`() {
        val groups = dummyRepo.filterAppGroups(socialCatalog, "instagram ads")
        assertEquals("com.instagram.android", groups.single().packageName)
        assertEquals(listOf("owner/social"), groups.single().bundles.map { it.repo })
        assertTrue(dummyRepo.filterAppGroups(socialCatalog, "instagram nonexistent").isEmpty())
    }

    @Test
    fun `package search distinguishes apps sharing the same display name`() {
        val groups = dummyRepo.filterAppGroups(socialCatalog, "com.facebook.lite")
        assertEquals("com.facebook.lite", groups.single().packageName)
        assertEquals("owner/lite", groups.single().bundles.single().repo)
    }

    @Test
    fun `source and patch search still find all supported apps`() {
        val groups = dummyRepo.filterAppGroups(socialCatalog, "ads")
        assertEquals(setOf("com.instagram.android", "com.facebook.katana"), groups.map { it.packageName }.toSet())
        assertTrue(groups.all { it.bundles.single().repo == "owner/social" })
        assertEquals("owner/general", dummyRepo.filterAppGroups(socialCatalog, "owner/general")
            .single().bundles.single().repo)
    }

    @Test
    fun `empty query returns all bundles ordered by stars`() {
        val results = dummyRepo.filterBundles(sampleBundles, sampleStore, "")
        assertEquals(3, results.size)
        assertEquals("Morphe Patches", results[0].name) // 120 stars
        assertEquals("Music Patches", results[1].name)  // 50 stars
        assertEquals("Shikify", results[2].name)        // 15 stars
    }

    @Test
    fun `matches by bundle name`() {
        val results = dummyRepo.filterBundles(sampleBundles, sampleStore, "shikify")
        assertEquals(1, results.size)
        assertEquals("Shikify", results[0].name)
    }

    @Test
    fun `matches by repo owner or path`() {
        val results = dummyRepo.filterBundles(sampleBundles, sampleStore, "0xRJDev")
        assertEquals(1, results.size)
        assertEquals("Shikify", results[0].name)
    }

    @Test
    fun `matches by app display name in store`() {
        val results = dummyRepo.filterBundles(sampleBundles, sampleStore, "youtube")
        assertEquals(1, results.size)
        assertEquals("Morphe Patches", results[0].name)
    }

    @Test
    fun `matches by app package name`() {
        val results = dummyRepo.filterBundles(sampleBundles, sampleStore, "com.spotify.music")
        assertEquals(1, results.size)
        assertEquals("Music Patches", results[0].name)
    }

    @Test
    fun `matches by patch name`() {
        val results = dummyRepo.filterBundles(sampleBundles, sampleStore, "sponsorblock")
        assertEquals(1, results.size)
        assertEquals("Morphe Patches", results[0].name)
    }

    @Test
    fun `matches multi-word query across fields`() {
        val results = dummyRepo.filterBundles(sampleBundles, sampleStore, "reddit material")
        assertEquals(1, results.size)
        assertEquals("Shikify", results[0].name)
    }

    @Test
    fun `deserializes full catalog schema with extra fields gracefully`() {
        val rawJson = """
            {
              "bundles": [
                {
                  "source": "github",
                  "repo": "test-owner/test-repo",
                  "name": "Test Repo",
                  "author": "test-owner",
                  "patchCount": 5,
                  "stars": 10,
                  "unknown_future_field": "ignore me"
                }
              ],
              "store": {
                "com.test.app": {
                  "name": "Test App",
                  "category": "Tools"
                }
              },
              "extra_root_field": 123
            }
        """.trimIndent()

        val catalog = json.decodeFromString<PatchSourceCatalog>(rawJson)
        assertEquals(1, catalog.bundles.size)
        assertEquals("Test Repo", catalog.bundles[0].name)
        assertEquals("Test App", catalog.store["com.test.app"]?.name)
    }
}
