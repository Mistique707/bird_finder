package com.example.birdfinder.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.birdfinder.BirdFinderApp
import com.example.birdfinder.settings.SettingsStore
import com.example.birdfinder.ui.about.AboutScreen
import com.example.birdfinder.ui.detail.DetailScreen
import com.example.birdfinder.ui.history.HistoryScreen
import com.example.birdfinder.ui.listen.ListenScreen
import com.example.birdfinder.ui.settings.SettingsScreen
import com.example.birdfinder.ui.splash.SplashScreen
import com.example.birdfinder.ui.theme.BirdFinderTheme
import com.example.birdfinder.ui.theme.Brand
import com.example.birdfinder.ui.theme.appBackgroundBrush

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext as BirdFinderApp
        setContent {
            val settings by app.settings.state
                .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT)
            BirdFinderTheme(themeMode = settings.themeMode) {
                AppRoot()
            }
        }
    }
}

private object Routes {
    const val LISTEN = "listen"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"
}

private enum class Tab(
    val route: String,
    val label: String,
    val on: ImageVector,
    val off: ImageVector,
) {
    Listen(Routes.LISTEN, "Listen", Icons.Filled.Hearing, Icons.Outlined.Hearing),
    History(Routes.HISTORY, "History", Icons.Filled.History, Icons.Outlined.History),
    Settings(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
private fun AppRoot() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val isTab = current == Routes.LISTEN || current == Routes.HISTORY || current == Routes.SETTINGS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush()),
    ) {
        NavHost(
            navController = nav,
            startDestination = Routes.LISTEN,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) {
            tabs(
                onOpenDetail = { id -> nav.navigate(Routes.detail(id)) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
                onBack = { nav.popBackStack() },
            )
        }

        if (isTab && !showSplash) {
            FloatingNav(
                current = current,
                onSelect = { route -> nav.navigateTopLevel(route) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }

        if (showSplash) {
            SplashScreen(onDone = { showSplash = false })
        }
    }
}

@Composable
private fun FloatingNav(
    current: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                NavPill(tab = tab, selected = current == tab.route, onClick = { onSelect(tab.route) })
            }
        }
    }
}

@Composable
private fun RowScope.NavPill(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) Brand.SkyBlue else Color.Transparent,
        label = "navBg",
    )
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bg,
        modifier = Modifier.weight(1f, fill = false),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) tab.on else tab.off,
                contentDescription = tab.label,
                tint = content,
            )
            if (selected) {
                Text(
                    "  ${tab.label}",
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun NavGraphBuilder.tabs(
    onOpenDetail: (Long) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    composable(Routes.LISTEN) { ListenScreen(onOpenDetail = onOpenDetail) }
    composable(Routes.HISTORY) { HistoryScreen(onOpenDetail = onOpenDetail) }
    composable(Routes.SETTINGS) { SettingsScreen(onOpenAbout = onOpenAbout) }
    composable(Routes.ABOUT) { AboutScreen(onBack = onBack) }
    composable(
        Routes.DETAIL,
        enterTransition = { slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(260)) },
        popExitTransition = { slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(220)) },
    ) { entry ->
        val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
        DetailScreen(detectionId = id, onBack = onBack)
    }
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
