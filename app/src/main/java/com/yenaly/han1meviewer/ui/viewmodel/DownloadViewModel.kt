package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.yenaly_libs.base.YenalyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : YenalyViewModel(application) {

    private val _downloaded = MutableStateFlow(mutableListOf<VideoWithCategories>())
    val downloaded = _downloaded.asStateFlow()

    val downloadedGroups: StateFlow<List<DownloadGroupEntity>> =
        DatabaseRepo.HanimeDownload.getAllGroups()
            .flowOn(Dispatchers.IO)
            .catch { e ->
                e.printStackTrace()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun loadAllDownloadingHanime() =
        DatabaseRepo.HanimeDownload.loadAllDownloadingHanime()
            .catch { e -> e.printStackTrace() }
            .flowOn(Dispatchers.IO)

    fun loadAllDownloadedHanime(
        sortedBy: HanimeDownloadEntity.SortedBy = HanimeDownloadEntity.SortedBy.ID,
        ascending: Boolean = false,
    ) {
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.loadAllDownloadedHanime(sortedBy, ascending)
                .catch { e -> e.printStackTrace() }
                .flowOn(Dispatchers.IO)
                .collect {
                    _downloaded.value = it
                }
        }
    }

    fun updateVideoGroup(videoCode: String, selectedGroupId: Int) {
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.updateVideoGroup(videoCode, selectedGroupId)
        }
    }

    fun createNewGroup(groupName: String){
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.createNewGroup(groupName)
        }
    }

    fun updateGroupName(groupId: Int, newName: String){
        viewModelScope.launch {
            try {
                val oldGroupName = DatabaseRepo.HanimeDownload.getGroupById(groupId)
                if (oldGroupName != null){
                    val updatedGroup = oldGroupName.copy(name = newName)
                    DatabaseRepo.HanimeDownload.updateGroup(updatedGroup)
                }
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }
    fun deleteGroup(group: DownloadGroupEntity){
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.deleteGroup(group)
        }
    }

    fun updateDownloadHanime(entity: HanimeDownloadEntity) {
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.update(entity)
        }
    }

    fun deleteDownloadHanimeBy(videoCode: String, quality: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseRepo.HanimeDownload.delete(videoCode, quality)
        }
    }
}
