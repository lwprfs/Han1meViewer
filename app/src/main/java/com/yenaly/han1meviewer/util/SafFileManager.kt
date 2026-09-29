package com.yenaly.han1meviewer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yenaly.han1meviewer.APP_NAME
import com.yenaly.han1meviewer.HFileManager.DEF_VIDEO_COVER_TYPE
import com.yenaly.han1meviewer.HFileManager.HANIME_DOWNLOAD_FOLDER
import com.yenaly.han1meviewer.HFileManager.createVideoCoverName
import com.yenaly.han1meviewer.HFileManager.getAppDownloadFolder
import com.yenaly.han1meviewer.HFileManager.getDownloadVideoCoverFile
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.download.HanimeDownloadDao
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object SafFileManager {

    const val KEY_TREE_URI = "saf_download_path"
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "flv", "mov", "webm")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private fun isSafReady(): Boolean = !Preferences.safDownloadPath.isNullOrBlank()
    fun buildOpenDirectoryIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra("android.provider.extra.SHOW_ADVANCED", true)
        }
    }

    fun persistUriPermission(context: Context, data: Intent?) {
        val treeUri = data?.data ?: return
        val contentResolver = context.contentResolver
        val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(treeUri, flags)
        Preferences.preferenceSp.edit {
            putString(KEY_TREE_URI, treeUri.toString())
        }
    }

    fun getSavedUri(): Uri? {
        val uriStr = Preferences.safDownloadPath
        return uriStr?.toUri()
    }

    fun getAppDownloadFolderSaf(context: Context): DocumentFile? {
        if (!isSafReady()) return null
        val uri = runCatching { Preferences.safDownloadPath?.toUri() }.getOrNull() ?: return null
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return null
        if (!tree.isDirectory) return null
        tree.ensureNoMedia()
        return tree
    }

    fun getDownloadVideoFolderSaf(context: Context, videoCode: String): DocumentFile? {
        val root = getAppDownloadFolderSaf(context) ?: return null
        val hanime = root.ensureChildDir(HANIME_DOWNLOAD_FOLDER) ?: return null
        val videoDir = hanime.ensureChildDir(videoCode) ?: return null
        videoDir.ensureNoMedia()
        return videoDir
    }

    fun getDownloadVideoCoverDoc(
        context: Context,
        videoCode: String,
        title: String,
        suffix: String = DEF_VIDEO_COVER_TYPE
    ): DocumentFile? {
        val dir = getDownloadVideoFolderSaf(context, videoCode) ?: return null
        val name = createVideoCoverName(title, suffix)
        dir.findFile(name)?.let { return it }
        return dir.createFile(mimeForExt(suffix), name)
    }

    fun openOutputStreamForCover(
        context: Context,
        videoCode: String,
        title: String,
        suffix: String = DEF_VIDEO_COVER_TYPE
    ): Pair<OutputStream?, Uri?> {
        return if (isSafReady()) {
            val doc = getDownloadVideoCoverDoc(context, videoCode, title, suffix) ?: return Pair(
                null,
                null
            )
            val os = context.contentResolver.openOutputStream(doc.uri, "rwt")
            Pair(os, doc.uri)
        } else {
            val file = getDownloadVideoCoverFile(context, videoCode, title, suffix)
            file.parentFile?.mkdirs()
            Pair(FileOutputStream(file), file.toUri())
        }
    }

    private fun DocumentFile.ensureChildDir(name: String): DocumentFile? {
        findFile(name)?.let { return if (it.isDirectory) it else null }
        return createDirectory(name)
    }

    private fun DocumentFile.ensureNoMedia() {
        if (findFile(".nomedia") == null) {
            createFile("application/octet-stream", ".nomedia")
        }
    }

    fun getDownloadVideoFileUri(
        context: Context,
        videoCode: String,
        fileName: String
    ): Uri? {
        if (!isSafReady()) return null
        val treeUri = Preferences.safDownloadPath?.toUri() ?: return null
        val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val rootDir = docTree.findFile(HANIME_DOWNLOAD_FOLDER)
            ?: docTree.createDirectory(HANIME_DOWNLOAD_FOLDER)
            ?: return null
        val videoDir = rootDir.findFile(videoCode)
            ?: rootDir.createDirectory(videoCode)
            ?: return null
        val ext = fileName.substringAfterLast('.', "")
        val mimeType = mimeForExt(ext)
        val target = videoDir.findFile(fileName)
            ?: videoDir.createFile(mimeType, fileName)
            ?: return null

        return target.uri
    }

    private fun mimeForExt(ext: String): String = when (ext.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    fun migratePrivateToSaf(
        context: Context,
        dao: HanimeDownloadDao? = null,
        onProgress: ((migrated: Int, total: Int) -> Unit)? = null
    ) = CoroutineScope(Dispatchers.IO).launch {
        val privateFolder =
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APP_NAME)
        val hanimeDownloadFolder = File(privateFolder, HANIME_DOWNLOAD_FOLDER)

        if (!hanimeDownloadFolder.exists() || !hanimeDownloadFolder.isDirectory) {
            Log.e("Migrate", "hanime_download 文件夹不存在")
            withContext(Dispatchers.Main) {
                onProgress?.invoke(0, 0)
            }
            return@launch
        }

        val treeUri = Preferences.safDownloadPath?.toUri()
        if (treeUri == null) {
            withContext(Dispatchers.Main) {
                onProgress?.invoke(0, -1)
            }
            Log.e("Migrate", "SAF treeUri 为空")
            return@launch
        }

        val rootDocFile = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDocFile == null) {
            withContext(Dispatchers.Main) {
                onProgress?.invoke(0, -1)
            }
            Log.e("Migrate", "无法获取 DocumentFile 根目录")
            return@launch
        }

        val hanimeDownloadDoc = rootDocFile.findFile(HANIME_DOWNLOAD_FOLDER)
            ?: rootDocFile.createDirectory(HANIME_DOWNLOAD_FOLDER)

        if (hanimeDownloadDoc == null) {
            withContext(Dispatchers.Main) {
                onProgress?.invoke(0, -1)
            }
            Log.e("Migrate", "创建/获取 HANIME_DOWNLOAD_FOLDER 失败")
            return@launch
        }

        val folders = hanimeDownloadFolder.listFiles { it.isDirectory } ?: arrayOf()
        val total = folders.size
        var migrated = 0

        Log.d("Migrate", "开始迁移，总文件夹数: $total")

        for (folder in folders) {
            Log.d("Migrate", "正在迁移文件夹: ${folder.name}")

            var folderDoc = hanimeDownloadDoc.findFile(folder.name)

            if (folderDoc == null) {
                folderDoc = hanimeDownloadDoc.createDirectory(folder.name)
                if (folderDoc == null) {
                    Log.e("Migrate", "创建视频文件夹失败: ${folder.name}")
                    continue
                }
            } else {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val existingFile = folderDoc.findFile(file.name)
                        if (existingFile != null) {
                            existingFile.delete()
                            Log.d("Migrate", "删除冲突文件: ${file.name}")
                        }
                    }
                }
            }

            folder.listFiles()?.forEach { file ->
                if (file.isFile) {
                    if (folderDoc.findFile(file.name) == null) {
                        val mimeType = mimeForExt(file.extension.lowercase())
                        val newFile = folderDoc.createFile(mimeType, file.name)
                        if (newFile == null) {
                            Log.e("Migrate", "创建文件失败: ${file.name}")
                            return@forEach
                        }

                        try {
                            file.inputStream().use { input ->
                                context.contentResolver.openOutputStream(newFile.uri)
                                    ?.use { output ->
                                        input.copyTo(output)
                                    }
                            }
                            Log.d("Migrate", "已迁移文件: ${file.name}")
                        } catch (e: Exception) {
                            Log.e("Migrate", "复制文件出错: ${file.name}", e)
                        }
                    } else {
                        Log.d("Migrate", "文件已存在，跳过: ${file.name}")
                    }
                }
            }

            folder.deleteRecursively()

            migrated++
            Log.d("Migrate", "已完成文件夹: ${folder.name} ($migrated/$total)")

            withContext(Dispatchers.Main) {
                onProgress?.invoke(migrated, total)
            }
        }
        try {
            if (dao != null) {
                scanAndImportHanimeDownloads(context, dao)
            }
        } catch (e: Exception) {
            Log.e("saf", e.message.toString())
        }
        withContext(Dispatchers.Main) {
            onProgress?.invoke(migrated, total)
        }
        Log.d("Migrate", "迁移完成，总文件夹数: $total")
    }

    enum class ImportFailureReason {
        INVALID_FOLDER_NAME,
        MISSING_INFO_JSON,
        IMPORT_EXCEPTION,
    }

    data class ImportFailure(
        val name: String,
        val reason: ImportFailureReason,
    )

    data class ImportResult(
        val successCount: Int = 0,
        val failures: List<ImportFailure> = emptyList(),
    ) {
        val failureCount get() = failures.size
    }

    suspend fun scanAndImportHanimeDownloads(
        context: Context,
        dao: HanimeDownloadDao,
        onProgress: ((imported: Int, total: Int, currentName: String?) -> Unit)? = null,
    ): ImportResult {
        val treeUri = Preferences.safDownloadPath?.toUri() ?: return ImportResult()
        val rootDocFile = DocumentFile.fromTreeUri(context, treeUri) ?: return ImportResult()
        val hanimeDownloadDoc = rootDocFile.findFile(HANIME_DOWNLOAD_FOLDER) ?: return ImportResult()

        val folders = hanimeDownloadDoc.listFiles().filter { it.isDirectory }
        val total = folders.size
        val failures = mutableListOf<ImportFailure>()
        var successCount = 0

        folders.forEachIndexed { index, folderDoc ->
            var currentName: String? = folderDoc.name
            var failure: ImportFailure? = null
            try {
                val videoCode = folderDoc.name
                if (videoCode.isNullOrBlank()) {
                    failure = ImportFailure(
                        folderDoc.uri.lastPathSegment ?: "?",
                        ImportFailureReason.INVALID_FOLDER_NAME,
                    )
                } else {
                    try {
                        val infoFile = folderDoc.findFile("info.json")
                        if (infoFile == null) {
                            failure = ImportFailure(videoCode, ImportFailureReason.MISSING_INFO_JSON)
                        } else {
                            val inputStream = context.contentResolver.openInputStream(infoFile.uri)
                            if (inputStream == null) {
                                failure = ImportFailure(videoCode, ImportFailureReason.MISSING_INFO_JSON)
                            } else {
                                inputStream.use { input ->
                                    val json = input.bufferedReader().use { it.readText() }
                                    val jsonObj =
                                        kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject

                                    val title = jsonObj["title"]?.jsonPrimitive?.content ?: ""
                                    val coverUrl = jsonObj["coverUrl"]?.jsonPrimitive?.content ?: ""
                                    val addDate = System.currentTimeMillis()
                                    val videoUrls = jsonObj["videoUrls"]?.jsonObject

                                    val videoFile = folderDoc.listFiles()
                                        .firstOrNull { file ->
                                            file.isFile && file.name?.substringAfterLast('.', "")
                                                ?.lowercase() in VIDEO_EXTENSIONS
                                        }
                                    val pattern = """_(\d+P)\.mp4$""".toRegex()
                                    val quality = pattern.find(videoFile?.name.toString())?.groupValues?.get(1)
                                        ?: "unknow"
                                    val videoUrl = videoUrls?.get(quality)
                                        ?.jsonObject
                                        ?.get("link")
                                        ?.jsonPrimitive
                                        ?.content

                                    val coverFile = folderDoc.listFiles()
                                        .firstOrNull { file ->
                                            file.isFile && file.name?.substringAfterLast('.', "")
                                                ?.lowercase() in IMAGE_EXTENSIONS
                                        }

                                    val videoUri = videoFile?.uri?.toString() ?: ""
                                    val coverUri = coverFile?.uri?.toString()
                                    val videoLength = videoFile?.length() ?: 0L
                                    currentName = videoFile?.name ?: videoCode
                                    val existing = dao.find(videoCode)
                                    if (existing != null) {
                                        val updated = existing.copy(
                                            videoUri = videoUri,
                                            coverUri = coverUri,
                                            length = videoLength,

                                        )
                                        dao.update(updated)
                                        Log.d("ImportHanime", "已存在，更新 videoUri/coverUri: $videoCode, length:$videoLength")
                                    } else {
                                        val entity = HanimeDownloadEntity(
                                            coverUrl = coverUrl,
                                            coverUri = coverUri,
                                            title = title,
                                            addDate = addDate,
                                            videoCode = videoCode,
                                            videoUri = videoUri,
                                            quality = quality,
                                            videoUrl = videoUrl.toString(),
                                            length = videoLength,
                                            downloadedLength = 0L,
                                            state = DownloadState.Finished
                                        )
                                        dao.insert(entity)
                                        Log.d("ImportHanime", "导入完成: $videoCode, length:$videoLength")
                                    }
                                    successCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        failure = ImportFailure(videoCode, ImportFailureReason.IMPORT_EXCEPTION)
                        Log.e("ImportHanime", "导入失败: $videoCode", e)
                    }
                }
            } finally {
                failure?.let { failures.add(it) }
                onProgress?.invoke(index + 1, total, currentName)
            }
        }
        return ImportResult(successCount, failures)
    }

    fun isValidHanimeFolder(
        folder: File,
        updateMetadata: (videoCode: String) -> Unit
    ): Boolean {
        if (!folder.exists() || !folder.isDirectory) return false
        val hasVideo = folder.listFiles()?.any { file ->
            file.isFile && VIDEO_EXTENSIONS.contains(file.extension.lowercase())
        } ?: false

        if (!hasVideo) return false

        val videoCode = folder.name
        updateMetadata(videoCode)
        return true
    }

    fun updateMetadata(
        context: Context,
        videoCode: String,
        uri: Uri
    ): Boolean {

        return true
    }

    fun checkSafPermissions(context: Context): Boolean {
        val treeUri = Preferences.safDownloadPath?.toUri() ?: return false
        val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return try {
            val testFile = docTree.createFile("text/plain", ".test_permission")
            val result = testFile != null
            testFile?.delete()
            result
        } catch (e: Exception) {
            Log.w("HFileMigrator", "SAF 权限检查失败", e)
            false
        }
    }

    fun deleteDownloadVideoFolder(context: Context, videoCode: String) {
        if (Preferences.isUsePrivateStorage) {

            val folder = File(getAppDownloadFolder(context), "$HANIME_DOWNLOAD_FOLDER/$videoCode")
            if (folder.exists()) folder.deleteRecursively()
        } else {

            val treeUri = Preferences.safDownloadPath?.toUri() ?: return
            val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: return

            val rootDir = docTree.findFile(HANIME_DOWNLOAD_FOLDER) ?: return
            val videoDir = rootDir.findFile(videoCode)
            if (videoDir != null && videoDir.isDirectory) {
                videoDir.delete()
            }
        }
    }
}
