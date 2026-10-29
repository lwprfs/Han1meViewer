package com.yenaly.han1meviewer.FapFolder

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants

object FapFolderSiteType {

    const val HOSTNAME = "fapfolder.club"

    fun isFapFolderSite(siteTypeName: String?): Boolean =
        siteTypeName.equals("FAPFOLDER", ignoreCase = true)

    fun displayUrl(): String = FapFolderConstants.BASE_URL

    fun defaultHostname(): String = HOSTNAME

    fun baseUrl(): String = FapFolderConstants.BASE_URL

    fun ajaxBase(): String = FapFolderConstants.AJAX_BASE

    fun uploadsBase(): String = FapFolderConstants.UPLOADS_BASE
}
