/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PatchSourceCatalog(
    val bundles: List<CatalogBundle> = emptyList(),
    val store: Map<String, CatalogStoreApp> = emptyMap(),
)

@Serializable
data class CatalogBundle(
    val source: String = "github",
    val repo: String,
    val name: String = "",
    val author: String? = null,
    val repoDescription: String? = null,
    val patchCount: Int = 0,
    val targetApps: List<String> = emptyList(),
    val stars: Int = 0,
    val updatedAt: Long = 0L,
    val avatarUrl: String? = null,
    val bundleIconUrl: String? = null,
    val deepLink: String? = null,
    val patches: List<CatalogPatch> = emptyList(),
)

@Serializable
data class CatalogPatch(
    val name: String = "",
    val default: Boolean = true,
)

@Serializable
data class CatalogStoreApp(
    val name: String = "",
    val iconUrl: String? = null,
    val category: String? = null,
    val description: String? = null,
)
