package com.yenaly.han1meviewer
import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object HFileManager {

    const val HANIME_DOWNLOAD_FOLDER = "hanime_download"
    const val DEF_VIDEO_TYPE = "mp4"
    const val DEF_VIDEO_COVER_TYPE = "png"
    val illegalCharsRegex = Regex("""["*/:<>?\\|\x00-\x1F\x7F]""")

    fun getAppDownloadFolder(context: Context): File {
        return if (Preferences.isUsePrivateStorage) {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APP_NAME
            )
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APP_NAME
            )
        }
    }

    fun getDownloadVideoFolder(context: Context, videoCode: String): File {
        val folder = File(getAppDownloadFolder(context), "$HANIME_DOWNLOAD_FOLDER/$videoCode")
        return folder
    }

    fun getDownloadVideoFile(
        context: Context,
        videoCode: String,
        title: String,
        quality: String,
        suffix: String = DEF_VIDEO_TYPE
    ): File {
        return File(
            getDownloadVideoFolder(context, videoCode),
            createVideoName(title, quality, suffix)
        )
    }

    fun getDownloadVideoCoverFile(
        context: Context,
        videoCode: String,
        title: String,
        suffix: String = DEF_VIDEO_COVER_TYPE
    ): File {
        return File(
            getDownloadVideoFolder(context, videoCode),
            createVideoCoverName(title, suffix)
        )
    }

    private fun String.replaceAllIllegalChars(): String =
        illegalCharsRegex.replace(this, "_")

    fun createVideoName(title: String, quality: String, suffix: String): String =
        "${title.replaceAllIllegalChars()}_${quality}.$suffix"

    fun createVideoCoverName(title: String, suffix: String): String =
        "${title.replaceAllIllegalChars()}.$suffix"

    @Deprecated("下载工具已经创建了nomedia，没必要重复创建")
    private fun File.makeFolderNoMedia() {
        if (!exists() && !mkdirs()) {
            Log.w("HFileManager", "⚠️ 目录创建失败: $absolutePath")
            return
        }

        if (!isDirectory) {
            Log.w("HFileManager", "⚠️ 已存在但不是文件夹: $absolutePath")
            return
        }

        val noMedia = File(this, ".nomedia")
        if (!noMedia.exists()) {
            runCatching { noMedia.createNewFile() }
                .onFailure { Log.w("HFileManager", "⚠️ 创建 .nomedia 失败: ${it.message}") }
        }
    }
}
