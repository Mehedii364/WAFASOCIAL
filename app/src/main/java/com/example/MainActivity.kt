package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutGoogle
import com.example.data.repository.WafaAiRepository
import com.example.data.repository.WafaCommunityMessagingRepository
import com.example.data.repository.WafaSocialRepository
import com.example.ui.screens.AdminControlPanelScreen
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CommunitiesEventsScreen
import com.example.ui.screens.CommunitySection
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.ExploreReelsScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PrivacySecurityScreen
import com.example.ui.screens.ProfileHubScreen
import com.example.ui.screens.SettingsAboutScreen
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreferenceRepository
import com.example.ui.theme.WafaSocialTheme
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.WafaSocialViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    EXPLORE("Explore", Icons.Rounded.Explore, Icons.Outlined.Explore),
    CREATE("Create", Icons.Rounded.AddBox, Icons.Outlined.AddBox),
    MESSAGES("Messages", Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Outlined.Chat),
    COMMUNITIES("Groups", Icons.Rounded.Groups, Icons.Outlined.Groups),
    PROFILE("Profile", Icons.Rounded.Person, Icons.Outlined.Person)
}

private enum class OverlayScreen {
    NONE,
    AI_STUDIO,
    CREATOR_STUDIO,
    NOTIFICATIONS,
    PRIVACY_SECURITY,
    ADMIN_PANEL,
    SETTINGS_ABOUT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge display
        enableEdgeToEdge()

        val themePrefs = ThemePreferenceRepository(applicationContext)

        setContent {
            val themeMode by themePrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val scope = rememberCoroutineScope()

            WafaSocialTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WafaAppRoot(
                        themeMode = themeMode,
                        onThemeModeChanged = { newMode ->
                            scope.launch { themePrefs.setThemeMode(newMode) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WafaAppRoot(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            WafaAuthenticatedShell(
                currentUserId = user.uid,
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged
            )
        }
    }
}

@Composable
private fun WafaAuthenticatedShell(
    currentUserId: String,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val socialViewModel: WafaSocialViewModel = viewModel(
        key = "social_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                WafaSocialViewModel(
                    socialRepo = WafaSocialRepository(db),
                    communityRepo = WafaCommunityMessagingRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val aiViewModel: AiAssistantViewModel = viewModel(
        key = "ai_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AiAssistantViewModel(
                    aiRepo = WafaAiRepository(),
                    socialRepo = WafaSocialRepository(db)
                )
            }
        }
    )

    LaunchedEffect(socialViewModel) {
        socialViewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var activeOverlay by remember { mutableStateOf(OverlayScreen.NONE) }
    var exploreQuery by remember { mutableStateOf("") }
    var communityInitialSection by remember { mutableStateOf(CommunitySection.COMMUNITIES) }

    val performSignOut: () -> Unit = {
        signOutGoogle(credentialManager, scope)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail && activeOverlay == OverlayScreen.NONE) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && activeOverlay == OverlayScreen.NONE) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                    ) {
                        when (activeOverlay) {
                            OverlayScreen.AI_STUDIO -> AiStudioScreen(
                                socialViewModel = socialViewModel,
                                aiViewModel = aiViewModel,
                                onBack = { activeOverlay = OverlayScreen.NONE },
                                onApplyToComposer = { generatedText ->
                                    socialViewModel.updateComposerDraft(generatedText.take(2000))
                                    activeOverlay = OverlayScreen.NONE
                                    activeTab = MainTab.CREATE
                                }
                            )
                            OverlayScreen.CREATOR_STUDIO -> CreatorStudioScreen(
                                viewModel = socialViewModel,
                                onBack = { activeOverlay = OverlayScreen.NONE }
                            )
                            OverlayScreen.NOTIFICATIONS -> NotificationsScreen(
                                viewModel = socialViewModel,
                                onBack = { activeOverlay = OverlayScreen.NONE }
                            )
                            OverlayScreen.PRIVACY_SECURITY -> PrivacySecurityScreen(
                                viewModel = socialViewModel,
                                onBack = { activeOverlay = OverlayScreen.NONE },
                                onSignOut = performSignOut
                            )
                            OverlayScreen.ADMIN_PANEL -> AdminControlPanelScreen(
                                viewModel = socialViewModel,
                                onBack = { activeOverlay = OverlayScreen.NONE }
                            )
                            OverlayScreen.SETTINGS_ABOUT -> SettingsAboutScreen(
                                themeMode = themeMode,
                                onThemeModeChanged = onThemeModeChanged,
                                onBack = { activeOverlay = OverlayScreen.NONE },
                                onSignOut = performSignOut
                            )
                            OverlayScreen.NONE -> {
                                when (activeTab) {
                                    MainTab.HOME -> HomeFeedScreen(
                                        viewModel = socialViewModel,
                                        onNavigateCreate = { activeTab = MainTab.CREATE },
                                        onOpenNotifications = { activeOverlay = OverlayScreen.NOTIFICATIONS },
                                        onOpenAiStudio = { activeOverlay = OverlayScreen.AI_STUDIO },
                                        onOpenEvents = {
                                            communityInitialSection = CommunitySection.EVENTS_LIVE
                                            activeTab = MainTab.COMMUNITIES
                                        },
                                        onSearchHashtag = { tag ->
                                            exploreQuery = "#$tag"
                                            activeTab = MainTab.EXPLORE
                                        }
                                    )
                                    MainTab.EXPLORE -> ExploreReelsScreen(
                                        viewModel = socialViewModel,
                                        initialQuery = exploreQuery,
                                        onOpenChat = { convId ->
                                            socialViewModel.openConversation(convId)
                                            activeTab = MainTab.MESSAGES
                                        },
                                        onNavigateCreate = { activeTab = MainTab.CREATE }
                                    )
                                    MainTab.CREATE -> CreatePostScreen(
                                        viewModel = socialViewModel,
                                        aiViewModel = aiViewModel,
                                        onPublished = { activeTab = MainTab.HOME }
                                    )
                                    MainTab.MESSAGES -> MessagesScreen(viewModel = socialViewModel)
                                    MainTab.COMMUNITIES -> CommunitiesEventsScreen(
                                        viewModel = socialViewModel,
                                        initialSection = communityInitialSection
                                    )
                                    MainTab.PROFILE -> ProfileHubScreen(
                                        viewModel = socialViewModel,
                                        onOpenCreatorStudio = { activeOverlay = OverlayScreen.CREATOR_STUDIO },
                                        onOpenAiStudio = { activeOverlay = OverlayScreen.AI_STUDIO },
                                        onOpenPrivacyCenter = { activeOverlay = OverlayScreen.PRIVACY_SECURITY },
                                        onOpenNotifications = { activeOverlay = OverlayScreen.NOTIFICATIONS },
                                        onOpenSettings = { activeOverlay = OverlayScreen.SETTINGS_ABOUT },
                                        onOpenAdminPanel = { activeOverlay = OverlayScreen.ADMIN_PANEL }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
