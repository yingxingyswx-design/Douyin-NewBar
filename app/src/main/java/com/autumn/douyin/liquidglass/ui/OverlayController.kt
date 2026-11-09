package com.autumn.douyin.liquidglass.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.nativebar.NativeBottomBar
import com.autumn.douyin.liquidglass.nativebar.NativeMessageBadgeMonitor

class OverlayController(private val nativeBar: NativeBottomBar) {
    var selectedTab by mutableIntStateOf(nativeBar.selectedIndex)
        private set
    var messageBadgeCount by mutableIntStateOf(0)
        private set

    private val messageBadgeMonitor = NativeMessageBadgeMonitor(nativeBar.messages) {
        messageBadgeCount = it
    }

    fun start() {
        messageBadgeMonitor.start()
    }

    fun stop() {
        messageBadgeMonitor.stop()
    }

    fun clickTab(index: Int) {
        if (index == selectedTab) {
            nativeBar.clickTab(index)
            return
        }
        selectedTab = index
        val accepted = nativeBar.clickTab(index)
        nativeBar.tabs[index].postDelayed({
            selectedTab = nativeBar.selectedIndex
        }, 260)
        ModuleLog.info("tab click index=$index accepted=$accepted")
    }

    fun clickPlus() {
        val accepted = nativeBar.clickPlus()
        ModuleLog.info("plus click accepted=$accepted")
    }

    fun longClickPlus(): Boolean {
        val accepted = nativeBar.longClickPlus()
        ModuleLog.info("plus long click accepted=$accepted")
        return accepted
    }
}
