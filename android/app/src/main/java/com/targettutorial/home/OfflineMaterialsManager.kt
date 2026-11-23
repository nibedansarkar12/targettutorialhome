package com.targettutorial.home

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.content.Intent
import android.webkit.URLUtil
import java.io.File

/**
 * Downloads public Google Drive PDF links to the student's own phone.
 * No Firebase Storage upload/download is used.
 *
 * This helper supports ordinary downloadable HTTPS URLs. Google Drive share links
 * may require public link access and may not always be direct-download URLs.
 */
object OfflineMaterialsManager {

    data class DownloadedFile(val id: Long, val title: String, val localUri: Uri?)

    fun enqueuePdf(
        context: Context,
        title: String,
        url: String
    ): Long {
        require(url.startsWith("https://")) { "শুধু HTTPS PDF link ব্যবহার করো।" }
        val request = DownloadManager.Request(Uri.parse(url.trim()))
            .setTitle(title.ifBlank { "Study Material" })
            .setDescription("Target Tutorial Home — PDF download")
            .setMimeType("application/pdf")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "TargetTutorial_${safeFileName(title)}.pdf"
            )
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    fun openDownloadedPdf(context: Context, downloadId: Long): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(downloadId) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun queryDownloadStatus(context: Context, downloadId: Long): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return -1
        cursor.use {
            if (!it.moveToFirst()) return -1
            return it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    fun getDownloadedUri(context: Context, downloadId: Long): Uri? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.getUriForDownloadedFile(downloadId)
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifBlank { "material" }
}
