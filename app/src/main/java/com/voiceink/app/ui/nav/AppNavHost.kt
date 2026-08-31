package com.voiceink.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.NavType
import com.voiceink.app.ui.detail.NoteDetailScreen
import com.voiceink.app.ui.capture.CaptureScreen
import com.voiceink.app.ui.home.HomeScreen
import com.voiceink.app.ui.insights.InsightsScreen
import com.voiceink.app.ui.settings.SettingsScreen
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.todo.TodoScreen

object Routes {
    const val Home = "home"
    const val Todo = "todo"
    const val Insights = "insights"
    const val Capture = "capture"
    const val Settings = "settings"
}

private val tabRoutes = listOf(Routes.Home, Routes.Todo, Routes.Insights)

/** 3 Tab + 中央 FAB 骨架（§11.3）：FAB 悬浮于 Tab 栏之上直达速记页 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTab = currentRoute in tabRoutes

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Paper,
            bottomBar = {
                if (isTab) {
                    TabBar(
                        currentRoute = currentRoute,
                        onTab = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
                modifier = Modifier.padding(padding)
            ) {
                composable(Routes.Home) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(Routes.Settings) },
                        onOpenNote = { id -> navController.navigate("detail/$id") }
                    )
                }
                composable(Routes.Todo) {
                    TodoScreen(onOpenNote = { id -> navController.navigate("detail/$id") })
                }
                composable(Routes.Insights) { InsightsScreen() }
                composable(
                    route = Routes.Capture + "?mode={mode}",
                    arguments = listOf(
                        navArgument("mode") {
                            nullable = true
                            defaultValue = null
                        }
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "voiceink://capture?mode={mode}" }
                    )
                ) { entry ->
                    CaptureScreen(
                        mode = entry.arguments?.getString("mode"),
                        onDone = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "detail/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType })
                ) {
                    NoteDetailScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Settings) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        if (isTab) {
            // 中央 FAB：悬浮于 Tab 栏之上（设计稿：fab 底距 100px，caption 底距 82px）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 82.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                    // 紫光晕外环
                    Box(
                        Modifier
                            .size(76.dp)
                            .border(1.dp, Accent12, CircleShape)
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Accent)
                            .clickable(onClick = { navController.navigate(Routes.Capture) })
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Create,
                            contentDescription = "记录灵感",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
                Text(
                    text = "记录灵感",
                    fontSize = 9.5.sp,
                    letterSpacing = 1.sp,
                    color = Accent,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(Routes.Home, "首页", Icons.Outlined.Home),
    TabSpec(Routes.Todo, "待办", Icons.Outlined.CheckCircle),
    TabSpec(Routes.Insights, "洞察", Icons.Outlined.AutoAwesome)
)

@Composable
private fun TabBar(currentRoute: String?, onTab: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Paper)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = Line, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(top = 9.dp)
        ) {
            for (tab in tabs) {
                val selected = currentRoute == tab.route
                val color = if (selected) Ink else Faint
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTab(tab.route) }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = color,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp,
                        fontWeight = FontWeight.Medium,
                        color = color
                    )
                }
            }
        }
    }
}
