package com.nuvio.app.features.downloads

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.media.MediaFormat
import android.net.Network
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.media3.exoplayer.MediaExtractorCompat
import androidx.media3.common.MimeTypes
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

internal class AndroidDownloadScheduler(val context: Context) {
    val store = AndroidDownloadStore(File(context.filesDir, "download-transfers"))
    val directory = File(context.filesDir, "downloads")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(item: DownloadItem): AndroidDownloadTransfer {
        val previous = store.get(item.fileName)
        val transfer = store.begin(item)
        val existing = previous?.generation == transfer.generation
        if (existing && Build.VERSION.SDK_INT >= 34) return transfer
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                check(jobScheduler().schedule(buildJob(transfer)) == JobScheduler.RESULT_SUCCESS) {
                    "Android could not start the download. Open the app and try again."
                }
            } else {
                val work = OneTimeWorkRequestBuilder<DownloadsTransferWorker>()
                    .setInputData(workDataOf(FILE_NAME to item.fileName, GENERATION to transfer.generation))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                val operation = WorkManager.getInstance(context).enqueueUniqueWork(
                    workName(transfer), if (existing) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, work,
                )
                operation.result.addListener({
                    try {
                        operation.result.get()
                    } catch (error: Exception) {
                        fail(transfer, error)
                        notifyCurrent(item.fileName)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        } catch (error: Exception) {
            fail(transfer, error)
        }
        return store.get(item.fileName) ?: transfer
    }

    @RequiresApi(34)
    internal fun buildJob(transfer: AndroidDownloadTransfer): JobInfo =
        JobInfo.Builder(transfer.jobId, ComponentName(context, DownloadsTransferJobService::class.java))
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setEstimatedNetworkBytes(transfer.item.totalBytes ?: JobInfo.NETWORK_BYTES_UNKNOWN.toLong(), 0L)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(PersistableBundle().apply {
                putString(FILE_NAME, transfer.item.fileName)
                putString(GENERATION, transfer.generation)
            })
            .build()

    @RequiresApi(34)
    private fun jobScheduler(): JobScheduler =
        context.getSystemService(JobScheduler::class.java).forNamespace(JOB_NAMESPACE)

    fun restore(item: DownloadItem): DownloadItem {
        var transfer = store.get(item.fileName)?.takeIf { it.item.id == item.id }
            ?: return if (item.status == DownloadStatus.Downloading) {
                item.copy(status = DownloadStatus.Paused, errorMessage = null)
            } else item
        if (transfer.item.status == DownloadStatus.Downloading && Build.VERSION.SDK_INT >= 34 &&
            jobScheduler().getPendingJob(transfer.jobId) == null) {
            pause(item.fileName)
            transfer = store.get(item.fileName) ?: transfer
        }
        return transfer.item
    }

    fun pause(fileName: String) {
        val transfer = store.get(fileName) ?: return
        if (transfer.item.status != DownloadStatus.Downloading) return
        val paused = store.update(fileName, transfer.generation) {
            it.copy(item = it.item.copy(status = DownloadStatus.Paused, errorMessage = null))
        }
        cancelScheduled(transfer)
        paused?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    fun remove(fileName: String) {
        val transfer = store.get(fileName)
        store.remove(fileName)
        transfer?.let(::cancelScheduled)
        transfer?.let { DownloadsLiveStatusPlatform.removeNotification(it.item.id) }
        cleanupScope.launch {
            lock(fileName).withLock {
                if (store.get(fileName) == null) {
                    if (transfer?.item?.folder != null) {
                        transfer.partialDocumentUri?.let { uri ->
                            runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri)) }
                        }
                        return@withLock
                    }
                    File(directory, "$fileName.part").delete()
                    DownloadSubtitleStorage(File(directory, fileName).toURI().toString()).remove()
                }
            }
        }
    }

    private fun cancelScheduled(transfer: AndroidDownloadTransfer) {
        if (Build.VERSION.SDK_INT >= 34) jobScheduler().cancel(transfer.jobId)
        else WorkManager.getInstance(context).cancelUniqueWork(workName(transfer))
    }

    suspend fun execute(
        transfer: AndroidDownloadTransfer,
        network: Network? = null,
        onProgress: (AndroidDownloadTransfer) -> Unit,
    ): Boolean = lock(transfer.item.fileName).withLock {
        val fileName = transfer.item.fileName
        if (!isActive(transfer)) return@withLock false
        val client = if (network != null) {
            downloadHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                .dns { network.getAllByName(it).toList() }
                .connectionPool(ConnectionPool())
                .build()
        } else downloadHttpClient
        try {
            val destination = File(directory, fileName)
            if (transfer.item.folder == null) DownloadSubtitles.prepare(transfer.item, destination.toURI().toString())
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            var lastProgressAt = 0L
            val onHeaders: (Long?, String?) -> Unit = { total, validator ->
                updateActive(transfer) { it.copy(validator = validator, item = it.item.copy(totalBytes = total)) }
            }
            val progress: (Long, Long?) -> Unit = { bytes, total ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgressAt >= 1_000L || bytes == total) {
                    lastProgressAt = now
                    updateActive(transfer) {
                        it.copy(item = it.item.copy(downloadedBytes = bytes, totalBytes = total))
                    }?.let(onProgress)
                }
            }
            val folder = transfer.item.folder
            if (folder != null) {
                downloadToFolder(transfer, folder, client, onHeaders, progress)
                return@withLock false
            }
            val partial = if (destination.isFile) destination else transferAndroidDownload(
                transfer.item, directory, transfer.validator, client, onHeaders, progress,
            )
            currentCoroutineContext().ensureActive()
            updateActive(transfer) { current ->
                if (partial != destination && !partial.renameTo(destination)) {
                    throw IOException("Could not finalize the downloaded file")
                }
                val bytes = destination.length()
                current.copy(item = current.item.copy(
                    status = DownloadStatus.Completed,
                    localFileUri = destination.toURI().toString(),
                    downloadedBytes = bytes,
                    totalBytes = bytes,
                    errorMessage = null,
                ))
            }
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            val retry = shouldRetryAndroidDownload(error, transfer.retryCount)
            if (retry) updateActive(transfer) { it.copy(retryCount = it.retryCount + 1) }
            else fail(transfer, error)
            retry
        } finally {
            if (network != null) withContext(NonCancellable + Dispatchers.IO) { client.connectionPool.evictAll() }
        }
    }

    private suspend fun downloadToFolder(
        transfer: AndroidDownloadTransfer,
        folder: DownloadFolder,
        client: OkHttpClient,
        onHeaders: (Long?, String?) -> Unit,
        progress: (Long, Long?) -> Unit,
    ) {
        val docs = AndroidDownloadDocuments(context.contentResolver, folder)
        val name = transfer.item.fileName
        val current = store.get(name) ?: return
        val partial = current.partialDocumentUri?.let(Uri::parse) ?: updateActive(transfer) {
            it.copy(partialDocumentUri = docs.createPartial(name).toString())
        }?.partialDocumentUri?.let(Uri::parse) ?: return
        val verified = current.readyToPublishBytes
        val recovered = verified?.let { docs.publishedAfterRestart(partial, name, it) }
        val bytes = verified ?: transferDownloadBytes(
            transfer.item, docs.size(partial), current.validator, client, { docs.open(partial, it) }, onHeaders, progress,
        )
        currentCoroutineContext().ensureActive()
        if (verified == null) {
            validateLocalVideo(partial)
            docs.checkNameAvailable(partial, name)
            updateActive(transfer) { it.copy(readyToPublishBytes = bytes) }
        }
        updateActive(transfer) {
            val completed = recovered ?: docs.publish(partial, name)
            it.copy(
                partialDocumentUri = null, readyToPublishBytes = null,
                item = it.item.copy(status = DownloadStatus.Completed, localFileUri = completed.toString(),
                    downloadedBytes = bytes, totalBytes = bytes, errorMessage = null),
            )
        }
    }

    private fun validateLocalVideo(uri: Uri) {
        val extractor = MediaExtractorCompat(context)
        try {
            extractor.setDataSource(uri, 0L)
            val videoIndex = (0 until extractor.trackCount).firstOrNull {
                MimeTypes.isVideo(extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME))
            } ?: throw DownloadFormatException()
            extractor.selectTrack(videoIndex)
            if (extractor.drmInitData != null || extractor.sampleTrackIndex < 0 ||
                extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                throw DownloadFormatException()
            }
        } catch (_: SecurityException) {
            throw DownloadFolderException()
        } catch (_: java.io.FileNotFoundException) {
            throw DownloadFolderException()
        } catch (_: Exception) {
            throw DownloadFormatException()
        } finally {
            extractor.release()
        }
    }

    fun isActive(transfer: AndroidDownloadTransfer): Boolean = store.get(transfer.item.fileName)?.let {
        it.generation == transfer.generation && it.item.status == DownloadStatus.Downloading
    } == true

    fun fail(transfer: AndroidDownloadTransfer, error: Exception) {
        updateActive(transfer) {
            val message = when {
                it.item.folder == null -> error.message
                error is DownloadHttpException || error is DownloadFormatException || error is DownloadFolderException ||
                    error is DownloadNameConflictException -> error.message
                else -> "Download interrupted. Check the connection and available space, then retry."
            }
            it.copy(item = it.item.copy(status = DownloadStatus.Failed, errorMessage = message ?: "Download failed"))
        }
    }

    fun notifyCurrent(fileName: String) {
        store.get(fileName)?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    private fun updateActive(
        transfer: AndroidDownloadTransfer,
        update: (AndroidDownloadTransfer) -> AndroidDownloadTransfer,
    ): AndroidDownloadTransfer? = store.update(transfer.item.fileName, transfer.generation) {
        if (it.item.status == DownloadStatus.Downloading) {
            update(it).let { changed -> changed.copy(item = changed.item.copy(updatedAtEpochMs = System.currentTimeMillis())) }
        } else it
    }

    private fun lock(fileName: String): Mutex = locks.getOrPut(fileName) { Mutex() }
    private fun workName(transfer: AndroidDownloadTransfer) = "nuvio-download-${transfer.jobId}"

    companion object {
        const val FILE_NAME = "download_file_name"
        const val GENERATION = "download_generation"
        const val JOB_NAMESPACE = "nuvio-downloads"
    }
}

internal fun shouldRetryAndroidDownload(error: Exception, retries: Int): Boolean =
    retries < 4 && when (error) {
        is DownloadFolderException, is DownloadFormatException, is DownloadNameConflictException -> false
        is DownloadHttpException -> error.statusCode == 408 || error.statusCode == 429 || error.statusCode in 500..599
        is IOException -> true
        else -> false
    }
