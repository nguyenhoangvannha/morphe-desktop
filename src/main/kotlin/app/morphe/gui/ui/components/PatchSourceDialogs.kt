/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.CatalogAppGroup
import app.morphe.gui.data.model.CatalogBundle
import app.morphe.gui.data.model.CatalogStoreApp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceCatalog
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.repository.PatchSourceCatalogRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.AvatarLoader
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.PatchSourceNaming
import app.morphe.morphe_desktop.generated.resources.*
import io.ktor.client.HttpClient
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private enum class AddSourceTab {
    CATALOG,
    REMOTE,
    LOCAL,
}

@Composable
internal fun AddPatchSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (PatchSource) -> Unit
) {
    val form = remember { PatchSourceFormState() }
    var activeTab by remember { mutableStateOf(AddSourceTab.CATALOG) }
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val patchSourceManager: PatchSourceManager = koinInject()
    val catalogRepository: PatchSourceCatalogRepository = koinInject()
    val httpClient: HttpClient = koinInject()
    val scope = rememberCoroutineScope()
    val localConfig = rememberLocalSourceConfig(engineConfigRepository)
    val developerOptions = localConfig.developerOptions
    val lastLocalPatchDir = localConfig.lastLocalPatchDir

    val allExistingSources by patchSourceManager.allSources.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var catalogState by remember { mutableStateOf<Result<PatchSourceCatalog>?>(null) }
    var isLoadingCatalog by remember { mutableStateOf(false) }

    fun loadCatalog(forceRefresh: Boolean = false) {
        scope.launch {
            isLoadingCatalog = true
            catalogState = runCatching { catalogRepository.getCatalog(forceRefresh) }
            isLoadingCatalog = false
        }
    }

    LaunchedEffect(Unit) {
        loadCatalog()
    }

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.add_source),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        maxWidth = 620.dp,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AddSourceTab.entries.forEach { tab ->
                        val isSelected = activeTab == tab
                        val label = when (tab) {
                            AddSourceTab.CATALOG -> stringResource(Res.string.patch_source_catalog_tab)
                            AddSourceTab.REMOTE -> stringResource(Res.string.patch_source_custom_tab)
                            AddSourceTab.LOCAL -> stringResource(Res.string.patch_source_dialog_local_file_label)
                        }
                        MorpheChoiceChip(
                            text = label,
                            active = isSelected,
                            onClick = { activeTab = tab },
                        )
                    }
                }

                when (activeTab) {
                    AddSourceTab.CATALOG -> {
                        SlimSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = stringResource(Res.string.patch_source_catalog_search_placeholder),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val catalog = catalogState?.getOrNull()
                        val appGroups = remember(catalog, searchQuery) {
                            if (catalog != null) {
                                catalogRepository.filterAppGroups(catalog, searchQuery)
                            } else emptyList()
                        }

                        val expandedApps = remember(searchQuery) { mutableStateMapOf<String?, Boolean>() }

                        val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 380.dp)
                                .clip(MorpheTheme.shapes.small)
                                .border(1.dp, borderColor, MorpheTheme.shapes.small)
                                .padding(4.dp)
                        ) {
                            when {
                                isLoadingCatalog -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = MorpheTheme.colors.primary,
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = stringResource(Res.string.patch_source_catalog_loading),
                                                style = MorpheTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                catalogState?.isFailure == true -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.patch_source_catalog_error),
                                                style = MorpheTheme.typography.bodySmall,
                                                color = MorpheTheme.colors.danger
                                            )
                                            MorpheButton(
                                                label = stringResource(Res.string.patch_source_catalog_retry),
                                                size = MorpheButtonSize.COMPACT,
                                                variant = MorpheButtonVariant.OUTLINED,
                                                onClick = { loadCatalog(forceRefresh = true) }
                                            )
                                        }
                                    }
                                }
                                appGroups.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.patch_source_catalog_empty),
                                            style = MorpheTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                else -> {
                                    val listState = rememberLazyListState()
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            appGroups.forEach { group ->
                                                val expanded = expandedApps[group.packageName] ?: searchQuery.isNotBlank()
                                                item(key = "app:${group.packageName}") {
                                                    CatalogAppHeader(
                                                        group = group,
                                                        expanded = expanded,
                                                        onClick = { expandedApps[group.packageName] = !expanded },
                                                    )
                                                }
                                                if (expanded) {
                                                    items(group.bundles, key = { "${group.packageName}:${it.source}:${it.repo}" }) { bundle ->
                                                        CatalogBundleItem(
                                                            bundle = bundle,
                                                            store = catalog?.store ?: emptyMap(),
                                                            isAdded = isBundleAdded(bundle, allExistingSources),
                                                            httpClient = httpClient,
                                                            onAdd = {
                                                                val provider = if (bundle.source.equals("gitlab", ignoreCase = true)) {
                                                                    PatchSourceType.GITLAB
                                                                } else {
                                                                    PatchSourceType.GITHUB
                                                                }
                                                                val canonicalUrl = if (provider == PatchSourceType.GITLAB) {
                                                                    "https://gitlab.com/${bundle.repo}"
                                                                } else {
                                                                    "https://github.com/${bundle.repo}"
                                                                }
                                                                onAdd(PatchSource(
                                                                    id = UUID.randomUUID().toString(),
                                                                    name = bundle.name.ifBlank { bundle.repo },
                                                                    type = provider,
                                                                    url = canonicalUrl,
                                                                    deletable = true,
                                                                    enabled = true,
                                                                    usePreRelease = false,
                                                                ))
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        VerticalScrollbar(
                                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                            adapter = rememberScrollbarAdapter(listState),
                                            style = MorpheScrollbarStyle()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    AddSourceTab.REMOTE -> {
                        LabeledField(label = stringResource(Res.string.patch_source_dialog_repo_url_label)) {
                            SlimTextField(
                                value = form.url,
                                onValueChange = { newUrl ->
                                    form.url = newUrl
                                    form.error = null
                                },
                                placeholder = stringResource(Res.string.patch_source_dialog_repo_placeholder),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(Res.string.patch_source_dialog_repo_hint),
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            val isPullRequest = RemotePatchSourceFactory.parse(form.url)?.provider == PatchProvider.GITHUB_PR
                            if (!isPullRequest) {
                                PatchSourceToggleRow(
                                    title = stringResource(Res.string.patch_source_dialog_pre_release_title),
                                    hint = stringResource(Res.string.patch_source_dialog_pre_release_hint),
                                    checked = form.usePreRelease,
                                    onCheckedChange = { form.usePreRelease = it },
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    AddSourceTab.LOCAL -> {
                        LocalSourceRow(
                            filePath = form.filePath,
                            developerOptions = developerOptions,
                            lastLocalPatchDir = lastLocalPatchDir,
                            onPicked = { path, suggested ->
                                form.filePath = path
                                if (form.name.isBlank()) form.name = suggested
                                form.error = null
                                scope.launch { engineConfigRepository.setLastLocalPatchDir(dirToRemember(path)) }
                            },
                        )
                    }
                }

                if (activeTab != AddSourceTab.CATALOG) {
                    form.error?.let {
                        Text(
                            text = it,
                            style = MorpheTheme.typography.labelSmall,
                            color = MorpheTheme.colors.danger
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (activeTab != AddSourceTab.CATALOG) {
                val errInvalidUrl = stringResource(Res.string.patch_source_dialog_error_invalid_url)
                val errInvalidFile = stringResource(Res.string.patch_source_dialog_error_invalid_file)
                MorpheButton(
                    label = stringResource(Res.string.patch_source_dialog_add_button),
                    onClick = {
                        when (activeTab) {
                            AddSourceTab.REMOTE -> {
                                val resolved = resolveRemoteSourceUrl(form.url.trim())
                                if (resolved == null) {
                                    form.error = errInvalidUrl; return@MorpheButton
                                }
                                val resolvedName = form.name.trim().ifBlank {
                                    PatchSourceNaming.suggestNameFromUrl(form.url.trim()) ?: "Unnamed"
                                }
                                onAdd(PatchSource(
                                    id = UUID.randomUUID().toString(),
                                    name = resolvedName,
                                    type = resolved.provider,
                                    url = resolved.canonicalUrl,
                                    deletable = true,
                                    usePreRelease = form.usePreRelease
                                ))
                                return@MorpheButton
                            }
                            AddSourceTab.LOCAL -> {
                                if (form.filePath.isBlank() || !File(form.filePath).exists()) {
                                    form.error = errInvalidFile; return@MorpheButton
                                }
                                val localFile = File(form.filePath)
                                scope.launch {
                                    val resolvedName = form.name.trim().ifBlank {
                                        withContext(Dispatchers.IO) { PatchBundleLoader.extractName(localFile) }
                                            ?: localFile.nameWithoutExtension
                                    }
                                    onAdd(PatchSource(
                                        id = UUID.randomUUID().toString(),
                                        name = resolvedName,
                                        type = PatchSourceType.LOCAL,
                                        url = null,
                                        filePath = form.filePath.trim(),
                                        deletable = true,
                                        usePreRelease = form.usePreRelease
                                    ))
                                }
                            }
                            else -> {}
                        }
                    },
                    variant = MorpheButtonVariant.PRIMARY,
                    size = MorpheButtonSize.COMPACT,
                )
            }
        },
        dismissButton = {
            MorpheButton(
                label = stringResource(if (activeTab == AddSourceTab.CATALOG) Res.string.close else Res.string.cancel),
                onClick = onDismiss,
                variant = MorpheButtonVariant.GHOST,
                size = MorpheButtonSize.COMPACT,
            )
        }
    )
}

@Composable
private fun CatalogAppHeader(
    group: CatalogAppGroup,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MorpheTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MorpheAppAvatar(
                name = group.name.ifBlank { stringResource(Res.string.patch_source_catalog_other_apps) },
                size = MorpheAvatarSize.MEDIUM,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = group.name.ifBlank { stringResource(Res.string.patch_source_catalog_other_apps) },
                    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                group.packageName?.let { packageName ->
                    Text(
                        text = packageName,
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(Res.string.patch_source_catalog_sources_count, group.bundles.size),
                    style = MorpheTheme.typography.labelSmall,
                    color = MorpheTheme.colors.primary,
                )
            }
            Icon(
                imageVector = MorpheIcons.ExpandMore,
                contentDescription = stringResource(if (expanded) Res.string.collapse else Res.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@Composable
private fun CatalogBundleItem(
    bundle: CatalogBundle,
    store: Map<String, CatalogStoreApp>,
    isAdded: Boolean,
    httpClient: HttpClient,
    onAdd: () -> Unit,
) {
    var avatarBitmap by remember(bundle.avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(bundle.avatarUrl) {
        val url = bundle.avatarUrl
        if (!url.isNullOrBlank()) {
            avatarBitmap = AvatarLoader.loadAvatar(url, httpClient)
        }
    }

    val shapes = MorpheTheme.shapes
    val colors = MorpheTheme.colors
    val appNames = remember(bundle.targetApps, store) {
        bundle.targetApps.map { store[it]?.name ?: it }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val bitmap = avatarBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
            } else {
                MorpheAppAvatar(
                    name = bundle.name.ifBlank { bundle.repo },
                    size = MorpheAvatarSize.MEDIUM,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bundle.name.ifBlank { bundle.repo },
                        style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (bundle.stars > 0) {
                        Text(
                            text = "★ ${bundle.stars}",
                            style = MorpheTheme.typography.labelSmall,
                            color = colors.warning,
                        )
                    }
                    if (bundle.patchCount > 0) {
                        Text(
                            text = stringResource(Res.string.patch_source_catalog_patches_count, bundle.patchCount),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = bundle.repo,
                    style = MorpheTheme.typography.labelSmall,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (appNames.isNotEmpty()) {
                    val appsDisplay = if (appNames.size > 3) {
                        appNames.take(3).joinToString(", ") + " +${appNames.size - 3}"
                    } else {
                        appNames.joinToString(", ")
                    }
                    Text(
                        text = stringResource(Res.string.patch_source_catalog_apps_label, appsDisplay),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!bundle.repoDescription.isNullOrBlank()) {
                    Text(
                        text = bundle.repoDescription,
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isAdded) {
                MorpheChoiceChip(
                    text = stringResource(Res.string.patch_source_catalog_added),
                    active = true,
                    icon = MorpheIcons.Check,
                    onClick = {},
                )
            } else {
                MorpheButton(
                    label = stringResource(Res.string.patch_source_dialog_add_button),
                    variant = MorpheButtonVariant.PRIMARY,
                    size = MorpheButtonSize.COMPACT,
                    onClick = onAdd,
                )
            }
        }
    }
}

private fun isBundleAdded(bundle: CatalogBundle, existingSources: List<PatchSource>): Boolean {
    val targetRepo = bundle.repo.trim().lowercase()
    return existingSources.any { src ->
        if (src.type == PatchSourceType.DEFAULT && RemotePatchSourceFactory.DEFAULT_REPO_PATH.lowercase() == targetRepo) {
            true
        } else {
            val parsed = src.url?.let(RemotePatchSourceFactory::parse)
            parsed != null && parsed.repoPath.lowercase() == targetRepo
        }
    }
}

@Composable
internal fun EditPatchSourceDialog(
    source: PatchSource,
    onDismiss: () -> Unit,
    onSave: (PatchSource) -> Unit
) {
    val form = remember(source) {
        PatchSourceFormState(
            initialName = source.name,
            initialUrl = source.url ?: "",
            initialFilePath = source.filePath ?: "",
            initialPreRelease = source.usePreRelease,
        )
    }
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    val localConfig = rememberLocalSourceConfig(engineConfigRepository)
    val developerOptions = localConfig.developerOptions
    val lastLocalPatchDir = localConfig.lastLocalPatchDir

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.patch_source_dialog_edit_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                val isPullRequest = RemotePatchSourceFactory.parse(form.url)?.provider == PatchProvider.GITHUB_PR
                if (source.deletable) {
                    Text(
                        text = when {
                            isPullRequest -> stringResource(Res.string.patch_source_dialog_type_github_pr)
                            source.type == PatchSourceType.GITHUB -> stringResource(Res.string.patch_source_dialog_type_github_repo)
                            source.type == PatchSourceType.GITLAB -> stringResource(Res.string.patch_source_dialog_type_gitlab_repo)
                            source.type == PatchSourceType.LOCAL -> stringResource(Res.string.patch_source_dialog_local_file_label)
                            else -> ""
                        },
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.primary
                    )

                    LabeledField(label = stringResource(Res.string.patch_source_dialog_name_label)) {
                        SlimTextField(
                            value = form.name,
                            onValueChange = { form.name = it; form.error = null },
                            placeholder = stringResource(Res.string.patch_source_dialog_name_placeholder),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    when (source.type) {
                        PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                            LabeledField(label = stringResource(Res.string.patch_source_dialog_repo_url_label)) {
                                SlimTextField(
                                    value = form.url,
                                    onValueChange = { form.url = it; form.error = null },
                                    placeholder = stringResource(Res.string.patch_source_dialog_repo_placeholder),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        PatchSourceType.LOCAL -> {
                            LocalSourceRow(
                                filePath = form.filePath,
                                developerOptions = developerOptions,
                                lastLocalPatchDir = lastLocalPatchDir,
                                onPicked = { path, _ ->
                                    form.filePath = path
                                    form.error = null
                                    scope.launch { engineConfigRepository.setLastLocalPatchDir(dirToRemember(path)) }
                                },
                            )
                        }
                        else -> {}
                    }
                }

                if (source.type != PatchSourceType.LOCAL) {
                    if (!isPullRequest) {
                        PatchSourceToggleRow(
                            title = stringResource(Res.string.patch_source_dialog_pre_release_title),
                            hint = stringResource(Res.string.patch_source_dialog_pre_release_hint),
                            checked = form.usePreRelease,
                            onCheckedChange = { form.usePreRelease = it },
                        )
                    }
                }

                form.error?.let {
                    Text(
                        text = it,
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.danger
                    )
                }
            }
        },
        confirmButton = {
            val errNameRequired = stringResource(Res.string.patch_source_dialog_error_name_required)
            val errInvalidUrl = stringResource(Res.string.patch_source_dialog_error_invalid_url)
            val errInvalidFile = stringResource(Res.string.patch_source_dialog_error_invalid_file)
            MorpheButton(
                label = stringResource(Res.string.save),
                onClick = {
                    if (form.name.isBlank()) { form.error = errNameRequired; return@MorpheButton }
                    when (source.type) {
                        PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                            // Re-resolve provider from URL in case the host was edited.
                            val resolved = resolveRemoteSourceUrl(form.url.trim())
                            if (resolved == null) {
                                form.error = errInvalidUrl; return@MorpheButton
                            }
                            onSave(source.copy(
                                name = form.name.trim(),
                                type = resolved.provider,
                                url = resolved.canonicalUrl,
                                usePreRelease = form.usePreRelease
                            ))
                            return@MorpheButton
                        }
                        PatchSourceType.LOCAL -> {
                            if (form.filePath.isBlank() || !File(form.filePath).exists()) {
                                form.error = errInvalidFile; return@MorpheButton
                            }
                        }
                        else -> {}
                    }
                    onSave(source.copy(
                        name = form.name.trim(),
                        filePath = if (source.type == PatchSourceType.LOCAL) form.filePath.trim() else source.filePath,
                        usePreRelease = form.usePreRelease
                    ))
                },
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.COMPACT,
            )
        },
        dismissButton = {
            MorpheButton(
                label = stringResource(Res.string.cancel),
                onClick = onDismiss,
                variant = MorpheButtonVariant.GHOST,
                size = MorpheButtonSize.COMPACT,
            )
        }
    )
}

/** Parsed remote source details with canonical URL and provider type. */
internal data class ResolvedRemoteSource(
    val canonicalUrl: String,
    val provider: PatchSourceType, // GITHUB or GITLAB only
)

/** Parses and maps a remote source URL to a [ResolvedRemoteSource]. */
internal fun resolveRemoteSourceUrl(input: String): ResolvedRemoteSource? {
    val parsed = RemotePatchSourceFactory.parse(input) ?: return null
    val type = when (parsed.provider) {
        PatchProvider.GITHUB, PatchProvider.GITHUB_PR -> PatchSourceType.GITHUB
        PatchProvider.GITLAB -> PatchSourceType.GITLAB
    }
    return ResolvedRemoteSource(canonicalUrl = parsed.canonicalUrl, provider = type)
}

/** Returns the directory path to remember for subsequent file picker invocations. */
private fun dirToRemember(path: String): String? =
    File(path).let { if (it.isDirectory) it.absolutePath else it.parent }

/** Local patch source picker row supporting file selection and developer folder sources. */
@Composable
private fun LocalSourceRow(
    filePath: String,
    developerOptions: Boolean,
    lastLocalPatchDir: String?,
    onPicked: (path: String, suggestedName: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun startDir(): String? {
        val fromCurrent = filePath.takeIf { it.isNotBlank() }?.let { File(it) }
            ?.let { if (it.isDirectory) it else it.parentFile }
            ?.takeIf { it.isDirectory }?.absolutePath
        return fromCurrent ?: lastLocalPatchDir?.takeIf { File(it).isDirectory }
    }

    val pickerSelectFileTitle = stringResource(Res.string.patch_source_dialog_picker_select_file)
    val pickerSelectFolderTitle = stringResource(Res.string.patch_source_dialog_picker_select_folder)
    LabeledField(label = if (developerOptions) stringResource(Res.string.patch_source_dialog_mpp_or_folder_label) else stringResource(Res.string.patch_source_dialog_mpp_label)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlimTextField(
                    value = filePath,
                    onValueChange = {},
                    placeholder = if (developerOptions) stringResource(Res.string.patch_source_dialog_path_placeholder_dev) else stringResource(Res.string.patch_source_dialog_path_placeholder),
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                )
                MorpheButton(
                    label = if (developerOptions) stringResource(Res.string.patch_source_dialog_file_button) else stringResource(Res.string.browse),
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                    onClick = {
                        scope.launch {
                            val picked = MorpheFilePicker.pickFile(
                                title = pickerSelectFileTitle,
                                startDir = startDir()?.let { File(it) },
                                extensions = listOf("mpp"),
                            )
                            if (picked != null) {
                                val manifestName = withContext(Dispatchers.IO) { PatchBundleLoader.extractName(picked) }
                                onPicked(picked.absolutePath, manifestName ?: picked.name.removeSuffix(".mpp"))
                            }
                        }
                    },
                )
                if (developerOptions) {
                    MorpheButton(
                        label = stringResource(Res.string.folder),
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                        onClick = {
                            scope.launch {
                                MorpheFilePicker.pickDirectory(
                                    title = pickerSelectFolderTitle,
                                    startDir = startDir()?.let { File(it) },
                                )?.let { dir -> onPicked(dir.absolutePath, dir.name) }
                            }
                        },
                    )
                }
            }
            if (developerOptions) {
                Text(
                    text = stringResource(Res.string.patch_source_dialog_folder_hint),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class LocalSourceConfig(val developerOptions: Boolean, val lastLocalPatchDir: String?)

@Composable
private fun rememberLocalSourceConfig(engineConfigRepository: EngineConfigRepository): LocalSourceConfig {
    val config by engineConfigRepository.configFlow.collectAsState()
    LaunchedEffect(Unit) {
        engineConfigRepository.loadConfig()
    }
    return LocalSourceConfig(
        developerOptions = config?.developerOptions ?: false,
        lastLocalPatchDir = config?.lastLocalPatchDir,
    )
}

@Composable
private fun PatchSourceToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MorpheSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accentColor = MorpheTheme.colors.primary,
        )
    }
}

private class PatchSourceFormState(
    initialName: String = "",
    initialUrl: String = "",
    initialFilePath: String = "",
    initialPreRelease: Boolean = false,
) {
    var name by mutableStateOf(initialName)
    var url by mutableStateOf(initialUrl)
    var filePath by mutableStateOf(initialFilePath)
    var error by mutableStateOf<String?>(null)
    var usePreRelease by mutableStateOf(initialPreRelease)
}


