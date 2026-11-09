package com.manichord.mgit.common

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** Turns a folder picked in the system file manager (ACTION_OPEN_DOCUMENT_TREE) back into a
 * real filesystem path, because JGit needs java.io.File rather than a content:// URI. */
object SafPaths {

    private val REMOVABLE_VOLUME = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")

    /** Null when the picked folder isn't backed by files this app can open directly, e.g. a
     * cloud provider. */
    fun resolveTreePath(uri: Uri): File? {
        if (!DocumentsContract.isTreeUri(uri)) return null
        val docId = DocumentsContract.getTreeDocumentId(uri)

        // Some providers hand out a raw absolute path as the document id
        if (docId.startsWith('/')) return File(docId).takeIf { it.isDirectory }

        val parts = docId.split(':', limit = 2)
        if (parts.size != 2) return null
        val volume = parts[0]
        val relative = parts[1]

        if (volume.equals("raw", ignoreCase = true)) {
            return File(if (relative.startsWith('/')) relative else "/$relative")
                .takeIf { it.isDirectory }
        }

        val root = when {
            volume.equals("primary", ignoreCase = true) ->
                @Suppress("DEPRECATION") Environment.getExternalStorageDirectory()
            volume.equals("home", ignoreCase = true) ->
                @Suppress("DEPRECATION") Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS)
            volume.equals("downloads", ignoreCase = true) ->
                @Suppress("DEPRECATION") Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS)
            REMOVABLE_VOLUME.matches(volume) -> File("/storage/$volume")
            else -> return null
        }
        return (if (relative.isEmpty()) root else File(root, relative)).takeIf { it.isDirectory }
    }
}
