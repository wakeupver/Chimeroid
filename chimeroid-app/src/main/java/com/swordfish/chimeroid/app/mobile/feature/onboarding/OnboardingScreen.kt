package com.swordfish.chimeroid.app.mobile.feature.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.settings.StorageBaseDirPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val ReadyColor = Color(0xFF1B8A5A)

private enum class SetupItemStatus { READY, REQUIRED, OPTIONAL, INVALID }

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp &&
        configuration.screenWidthDp >= 600

    var isCompleting by remember { mutableStateOf(false) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val pagerState = rememberPagerState(pageCount = { uiState.totalPages })

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) viewModel.setCurrentPage(pagerState.currentPage)
    }
    LaunchedEffect(uiState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) pagerState.animateScrollToPage(uiState.currentPage)
    }

    val romsFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let(viewModel::setRomsDirectory) }

    val baseDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshBaseDirectory() }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshAllFilesAccess() }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNotificationPermission() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAllFilesAccess()
                viewModel.refreshBaseDirectory()
                viewModel.refreshNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launchAllFilesAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            viewModel.refreshAllFilesAccess()
        } else {
            val pkg = Uri.parse("package:${context.packageName}")
            try {
                allFilesLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg),
                )
            } catch (_: ActivityNotFoundException) {
                allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    val launchNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshNotificationPermission()
        }
    }

    val launchBaseDirPicker: () -> Unit = {
        baseDirLauncher.launch(Intent(context, StorageBaseDirPicker::class.java))
    }

    val launchRomsFolderPicker: () -> Unit = { romsFolderPicker.launch(null) }

    val goToPage: (Int) -> Unit = { page ->
        scope.launch {
            pagerState.animateScrollToPage(page.coerceIn(0, uiState.totalPages - 1))
            viewModel.setCurrentPage(page)
        }
    }
    val onNext = { goToPage(pagerState.currentPage + 1) }
    val onPrevious = { goToPage(pagerState.currentPage - 1) }
    val onGetStarted = {
        if (!isCompleting && uiState.canContinue) {
            isCompleting = true
            scope.launch {
                delay(260)
                viewModel.completeOnboarding(onComplete)
            }
        }
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isCompleting) 0.3f else 1f,
        animationSpec = tween(280),
        label = "onboarding-alpha",
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (isCompleting) -28f else 0f,
        animationSpec = tween(300),
        label = "onboarding-offset",
    )

    val bgMotion = rememberInfiniteTransition(label = "bg")
    val orb1X by bgMotion.animateFloat(-18f, 42f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "o1x")
    val orb1Y by bgMotion.animateFloat(-12f, 34f, infiniteRepeatable(tween(6100), RepeatMode.Reverse), label = "o1y")
    val orb2X by bgMotion.animateFloat(20f, -56f, infiniteRepeatable(tween(6800), RepeatMode.Reverse), label = "o2x")
    val orb2Y by bgMotion.animateFloat(0f, 58f, infiniteRepeatable(tween(5600), RepeatMode.Reverse), label = "o2y")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                ),
            ),
    ) {

        Box(
            modifier = Modifier
                .padding(start = 28.dp)
                .size(180.dp)
                .graphicsLayer { translationX = orb1X; translationY = orb1Y }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 96.dp, end = 20.dp)
                .size(140.dp)
                .graphicsLayer { translationX = orb2X; translationY = orb2Y }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .graphicsLayer { alpha = contentAlpha; translationY = contentOffset },
        ) {
            val onboardingActions = OnboardingActions(
                onNext = onNext,
                onPrevious = onPrevious,
                onGetStarted = onGetStarted,
                onGrantAllFiles = launchAllFilesAccess,
                onGrantNotification = launchNotificationPermission,
                onPickBaseDir = launchBaseDirPicker,
                onPickRomsFolder = launchRomsFolderPicker,
            )

            if (isLandscape) {
                LandscapeContent(
                    uiState = uiState,
                    pagerState = pagerState,
                    bottomInset = bottomInset,
                    actions = onboardingActions,
                )
            } else {
                PortraitContent(
                    uiState = uiState,
                    pagerState = pagerState,
                    bottomInset = bottomInset,
                    actions = onboardingActions,
                )
            }
        }

        AnimatedVisibility(
            visible = isCompleting,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 1.02f, animationSpec = tween(120)),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 12.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = stringResource(R.string.onboarding_finishing),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private class OnboardingActions(
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onGetStarted: () -> Unit,
    val onGrantAllFiles: () -> Unit,
    val onGrantNotification: () -> Unit,
    val onPickBaseDir: () -> Unit,
    val onPickRomsFolder: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitContent(
    uiState: OnboardingUiState,
    pagerState: PagerState,
    bottomInset: Dp,
    actions: OnboardingActions,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(
                        start = 24.dp, end = 24.dp,
                        top = 48.dp, bottom = 160.dp + bottomInset,
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (page) {
                    0, 1, 2 -> OnboardingHero(page = page)
                    3 -> {
                        OnboardingHeroSetup()
                        Spacer(Modifier.height(32.dp))
                        OnboardingSetupContent(
                            uiState = uiState,
                            onGrantAllFiles = actions.onGrantAllFiles,
                            onGrantNotification = actions.onGrantNotification,
                            onPickBaseDir = actions.onPickBaseDir,
                            onPickRomsFolder = actions.onPickRomsFolder,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp + bottomInset)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingPageIndicator(
                currentPage = pagerState.currentPage,
                totalPages = uiState.totalPages,
                modifier = Modifier.padding(bottom = 24.dp),
            )
            OnboardingNavigation(
                currentPage = pagerState.currentPage,
                totalPages = uiState.totalPages,
                canContinue = uiState.canContinue,
                onNext = actions.onNext,
                onPrevious = actions.onPrevious,
                onGetStarted = actions.onGetStarted,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandscapeContent(
    uiState: OnboardingUiState,
    pagerState: PagerState,
    bottomInset: Dp,
    actions: OnboardingActions,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(start = 24.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (page) {
                    0, 1, 2 -> OnboardingHero(page = page, showSubtitle = false)
                    3 -> OnboardingHeroSetup(showSubtitle = false)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(bottom = 106.dp + bottomInset),
                contentAlignment = Alignment.Center,
            ) {
                when (page) {
                    0, 1, 2 -> {
                        val subtitleRes = when (page) {
                            0 -> R.string.onboarding_page_1_subtitle
                            1 -> R.string.onboarding_page_2_subtitle
                            else -> R.string.onboarding_page_3_subtitle
                        }
                        Text(
                            text = stringResource(subtitleRes),
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp, letterSpacing = 0.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).statusBarsPadding().padding(top = 32.dp),
                        )
                    }
                    3 -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        OnboardingSetupContent(
                            uiState = uiState,
                            onGrantAllFiles = actions.onGrantAllFiles,
                            onGrantNotification = actions.onGrantNotification,
                            onPickBaseDir = actions.onPickBaseDir,
                            onPickRomsFolder = actions.onPickRomsFolder,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 32.dp, bottom = 16.dp + bottomInset)
            .widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingPageIndicator(
            currentPage = pagerState.currentPage,
            totalPages = uiState.totalPages,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OnboardingNavigation(
            currentPage = pagerState.currentPage,
            totalPages = uiState.totalPages,
            canContinue = uiState.canContinue,
            onNext = actions.onNext,
            onPrevious = actions.onPrevious,
            onGetStarted = actions.onGetStarted,
        )
    }
    }
}

@Composable
private fun OnboardingHero(
    page: Int,
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true,
) {
    data class PageContent(val titleRes: Int, val subtitleRes: Int, val icon: ImageVector)
    val content = when (page) {
        0 -> PageContent(R.string.onboarding_page_1_title, R.string.onboarding_page_1_subtitle, Icons.Rounded.Gamepad)
        1 -> PageContent(R.string.onboarding_page_2_title, R.string.onboarding_page_2_subtitle, Icons.Rounded.SmartDisplay)
        else -> PageContent(R.string.onboarding_page_3_title, R.string.onboarding_page_3_subtitle, Icons.AutoMirrored.Rounded.LibraryBooks)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = 480.dp),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(content.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(36.dp))
        Text(
            text = stringResource(content.titleRes),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (showSubtitle) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(content.subtitleRes),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun OnboardingHeroSetup(
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.widthIn(max = 480.dp)) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (showSubtitle) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun OnboardingPageIndicator(currentPage: Int, totalPages: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (page in 0 until totalPages) {
            val isSelected = page == currentPage
            val width by animateFloatAsState(if (isSelected) 22f else 8f, tween(300), label = "ind-$page")
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    ),
            )
        }
    }
}

@Composable
private fun OnboardingNavigation(
    currentPage: Int,
    totalPages: Int,
    canContinue: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (currentPage > 0) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.onboarding_back),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (currentPage < totalPages - 1) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(
                        stringResource(R.string.onboarding_next),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            } else {
                Button(
                    onClick = onGetStarted,
                    enabled = canContinue,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Text(
                        stringResource(R.string.onboarding_get_started),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingSetupContent(
    uiState: OnboardingUiState,
    onGrantAllFiles: () -> Unit,
    onGrantNotification: () -> Unit,
    onPickBaseDir: () -> Unit,
    onPickRomsFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requiredCount = listOf(uiState.allFilesAccessGranted, uiState.romsDirectoryValid).count { it }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        SetupCard(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.onboarding_all_files_title),
            description = stringResource(R.string.onboarding_all_files_desc),
            status = if (uiState.allFilesAccessGranted) SetupItemStatus.READY else SetupItemStatus.REQUIRED,
            onClick = onGrantAllFiles,
        )

        Spacer(Modifier.height(8.dp))

        SetupCard(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.onboarding_notification_title),
            description = stringResource(R.string.onboarding_notification_desc),
            status = if (uiState.notificationGranted) SetupItemStatus.READY else SetupItemStatus.OPTIONAL,
            onClick = onGrantNotification,
        )

        Spacer(Modifier.height(8.dp))

        SetupCard(
            icon = Icons.Rounded.FolderOpen,
            title = stringResource(R.string.onboarding_base_dir_title),
            description = uiState.baseDirectoryPath?.let { "…/${it.substringAfterLast('/')}" }
                ?: stringResource(R.string.onboarding_base_dir_desc),
            status = if (uiState.baseDirectoryValid) SetupItemStatus.READY else SetupItemStatus.OPTIONAL,
            onClick = onPickBaseDir,
        )

        Spacer(Modifier.height(8.dp))

        SetupCard(
            icon = Icons.Rounded.FolderOpen,
            title = stringResource(R.string.onboarding_roms_title),
            description = uiState.romsDirectoryUri
                ?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') }
                ?: stringResource(R.string.onboarding_roms_desc),
            status = when {
                uiState.romsDirectoryUri == null -> SetupItemStatus.REQUIRED
                uiState.romsDirectoryValid -> SetupItemStatus.READY
                else -> SetupItemStatus.INVALID
            },
            onClick = onPickRomsFolder,
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.onboarding_hint_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.onboarding_hint_body, requiredCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: SetupItemStatus,
    onClick: () -> Unit,
) {
    val statusColor = when (status) {
        SetupItemStatus.READY -> ReadyColor
        SetupItemStatus.REQUIRED -> MaterialTheme.colorScheme.tertiary
        SetupItemStatus.OPTIONAL -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        SetupItemStatus.INVALID -> MaterialTheme.colorScheme.error
    }
    val statusText = stringResource(
        when (status) {
            SetupItemStatus.READY -> R.string.onboarding_status_ready
            SetupItemStatus.REQUIRED -> R.string.onboarding_status_required
            SetupItemStatus.OPTIONAL -> R.string.onboarding_status_optional
            SetupItemStatus.INVALID -> R.string.onboarding_status_invalid_folder
        },
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 2.dp,
        onClick = onClick,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.12f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor,
                    )
                }
            }
        }
    }
}
