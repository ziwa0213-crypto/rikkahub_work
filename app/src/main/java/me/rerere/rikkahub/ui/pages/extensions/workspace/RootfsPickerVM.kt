package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import me.rerere.workspace.rootfs.RootfsCatalog

class RootfsPickerVM(private val catalog: RootfsCatalog) : ViewModel() {
    val state = catalog.state
    init { catalog.refresh() }
    fun refresh() = catalog.refresh(forceRefresh = true)
}
