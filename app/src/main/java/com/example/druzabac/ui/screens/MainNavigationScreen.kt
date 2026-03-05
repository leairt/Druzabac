package com.example.druzabac.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun MainNavigationScreen(onSignOut: () -> Unit) {
    var selectedTab by remember { mutableStateOf(1) }
    var profileScreenKey by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            // Reset profile screen when returning to it
                            if (index == 2) {
                                profileScreenKey++
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> SearchScreen()
                1 -> HomeScreen()
                2 -> key(profileScreenKey) {
                    MyProfileScreen(
                        onNavigateToMyProfile = {
                            // Reset profile screen and switch to profile tab
                            profileScreenKey++
                            selectedTab = 2
                        },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}

enum class BottomNavItem(val label: String, val icon: ImageVector) {
    SEARCH("", Icons.Default.Search),
    HOME("", Icons.Filled.Home),
    PROFILE("", Icons.Filled.Person)
}

val HexagonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Hexagon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = listOf(
                androidx.compose.ui.graphics.vector.PathNode.MoveTo(12f, 2f),
                androidx.compose.ui.graphics.vector.PathNode.LineTo(21.5f, 7.5f),
                androidx.compose.ui.graphics.vector.PathNode.LineTo(21.5f, 16.5f),
                androidx.compose.ui.graphics.vector.PathNode.LineTo(12f, 22f),
                androidx.compose.ui.graphics.vector.PathNode.LineTo(2.5f, 16.5f),
                androidx.compose.ui.graphics.vector.PathNode.LineTo(2.5f, 7.5f),
                androidx.compose.ui.graphics.vector.PathNode.Close
            ),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)
        )
    }.build()
}

