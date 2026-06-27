package com.mckimquyen.notes

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.notes.ui.main.MainAct
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualGhostUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainAct::class.java)

    @Test
    fun ghostModeUITest() {
        // Mở app, chờ 2 giây cho UI load xong (và để user kịp nhìn)
        Thread.sleep(2000)

        // 1. Nhấn nút FAB để mở màn hình tạo Note
        onView(withId(R.id.fab)).perform(click())
        
        // Chờ 2 giây để user nhìn thấy màn hình Edit
        Thread.sleep(2000)

        // 2. Nhấn nút Back để quay lại Home
        pressBack()
        Thread.sleep(2000)

        // 3. Mở Navigation Drawer (Menu bên trái) bằng cách vuốt từ cạnh trái
        onView(withId(R.id.drawerLayout)).perform(androidx.test.espresso.action.ViewActions.swipeRight())
        Thread.sleep(2000)

        // 4. Nhấn vào mục Settings
        // Đôi khi NavigationView cần perform click vào NavigationMenuItemView
        onView(withId(R.id.drawerItemSettings)).perform(click())
        Thread.sleep(2000)

        // 5. Trở về màn hình chính
        pressBack()
        Thread.sleep(2000)
    }
}
