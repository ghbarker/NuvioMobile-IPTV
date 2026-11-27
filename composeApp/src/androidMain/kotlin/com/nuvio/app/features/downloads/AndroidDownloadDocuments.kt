package com.nuvio.app.features.downloads

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

internal class DownloadFolderException(cause: Exception? = null) :
    IOException("Download folder unavailable. Reconnect the drive and grant access to the same folder.", cause)

internal class DownloadNameConflictException : IOException("A file with this name already exists in the download folder.")

internal class DownloadFormatException :
    IOException("This playlist entry did not return a downloadable video file.")

/** Only document URIs created for this download are opened for writing or deletion. */
internal class AndroidDownloadDocuments(private val resolver: ContentResolver, folder: DownloadFolder) {
    private val tree = Uri.parse(folder.treeUri)
    private val parent = access { DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)) }

    fun createPartial(fileName: String): Uri = access {
        DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$fileName.part")
            ?: throw DownloadFolderException()
    }

    fun size(uri: Uri): Long = access {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0L) }
            ?: throw FileNotFoundException()
    }

    fun open(uri: Uri, offset: Long): FileOutputStream = access {
        val fd = resolver.openFileDescriptor(uri, if (offset == 0L) "rwt" else "rw")
            ?: throw DownloadFolderException()
        val stream = ParcelFileDescriptor.AutoCloseOutputStream(fd)
        try {
            if (offset > 0L) {
                if (stream.channel.size() != offset) throw DownloadFolderException()
                stream.channel.position(offset)
            }
            stream
        } catch (error: Exception) {
            stream.close()
            throw error
        }
    }

    fun checkNameAvailable(partial: Uri, fileName: String) = access {
        if (children().any { it.second == fileName && it.first != partial }) throw DownloadNameConflictException()
    }

    fun publish(partial: Uri, fileName: String): Uri = access {
        checkNameAvailable(partial, fileName)
        DocumentsContract.renameDocument(resolver, partial, fileName) ?: throw DownloadFolderException()
    }

    /** A provider may retain an opaque ID across renames, or change it to the new path. */
    fun publishedAfterRestart(partial: Uri, fileName: String, bytes: Long): Uri? = access {
        val children = children()
        if (children.any { it.first == partial && it.second != fileName }) return@access null
        children.firstOrNull { it.second == fileName }?.first?.takeIf { size(it) == bytes }
    }

    private fun children(): List<Pair<Uri, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        val result = if (Build.VERSION.SDK_INT >= 26) {
            resolver.query(children, columns, null, null)
        } else {
            resolver.query(children, columns, null, null, null)
        }
        return result?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)) to cursor.getString(1))
                }
            }
        } ?: throw DownloadFolderException()
    }

    private inline fun <T> access(block: () -> T): T = try {
        block()
    } catch (conflict: DownloadNameConflictException) {
        throw conflict
    } catch (error: Exception) {
        throw DownloadFolderException(error)
    }
}
