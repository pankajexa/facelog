package com.exargen.facelog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.exargen.facelog.ui.screen.attendance.AttendanceScreen
import com.exargen.facelog.ui.screen.enrollment.EnrollmentScreen
import com.exargen.facelog.ui.screen.groupscan.GroupScanScreen
import com.exargen.facelog.ui.screen.history.HistoryScreen
import com.exargen.facelog.ui.screen.gallery.PhotoGalleryScreen
import com.exargen.facelog.ui.screen.home.HomeScreen
import com.exargen.facelog.ui.screen.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val ATTENDANCE = "attendance/{type}"
    const val GROUP_SCAN = "group_scan/{type}"
    const val ENROLLMENT = "enrollment"
    const val HISTORY = "history"
    const val PHOTO_GALLERY = "photo_gallery"
    const val SETTINGS = "settings"

    fun attendance(type: String) = "attendance/$type"
    fun groupScan(type: String) = "group_scan/$type"
}

@Composable
fun FaceLogNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToCheckIn = {
                    navController.navigate(Routes.attendance("CHECK_IN"))
                },
                onNavigateToCheckOut = {
                    navController.navigate(Routes.attendance("CHECK_OUT"))
                },
                onNavigateToGroupCheckIn = {
                    navController.navigate(Routes.groupScan("CHECK_IN"))
                },
                onNavigateToGroupCheckOut = {
                    navController.navigate(Routes.groupScan("CHECK_OUT"))
                },
                onNavigateToEnrollment = {
                    navController.navigate(Routes.ENROLLMENT)
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToPhotoGallery = {
                    navController.navigate(Routes.PHOTO_GALLERY)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(
            route = Routes.ATTENDANCE,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "CHECK_IN"
            AttendanceScreen(
                attendanceType = type,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.GROUP_SCAN,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "CHECK_IN"
            GroupScanScreen(
                attendanceType = type,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ENROLLMENT) {
            EnrollmentScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PHOTO_GALLERY) {
            PhotoGalleryScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
