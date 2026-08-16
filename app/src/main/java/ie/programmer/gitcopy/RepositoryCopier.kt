package ie.programmer.gitcopy

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class RepositoryCopier(private val context: Context) {

    suspend fun copyToDownloads(
        repo: RepoSpec,
        onProgress: (String) -> Unit,
    ): CopyResult = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("Downloading ${repo.owner}/${repo.name}…")
            val archive = downloadArchive(repo)
            try {
                var files = 0
                unzipRepository(archive) { relativePath, input ->
                    writeToDownloads(repo, relativePath, input)
                    files++
                    if (files == 1 || files % 25 == 0) {
                        onProgress("Copied $files files…")
                    }
                }
                CopyResult(
                    fileCount = files,
                    destination = "Downloads/GitCopy/${repo.name}",
                )
            } finally {
                archive.delete()
            }
        }.getOrElse { throw CopyException(it.message ?: "Copy failed", it) }
    }

    suspend fun copyToTree(
        repo: RepoSpec,
        treeUri: Uri,
        onProgress: (String) -> Unit,
    ): CopyResult = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("The selected folder is unavailable")
            val repoRoot = root.findFile(repo.name)?.takeIf { it.isDirectory }
                ?: root.createDirectory(repo.name)
                ?: error("Could not create ${repo.name} in the selected folder")

            onProgress("Downloading ${repo.owner}/${repo.name}…")
            val archive = downloadArchive(repo)
            try {
                var files = 0
                unzipRepository(archive) { relativePath, input ->
                    writeToDocumentTree(repoRoot, relativePath, input)
                    files++
                    if (files == 1 || files % 25 == 0) {
                        onProgress("Copied $files files…")
                    }
                }
                CopyResult(fileCount = files, destination = "${repo.name} in selected folder")
            } finally {
                archive.delete()
            }
        }.getOrElse { throw CopyException(it.message ?: "Copy failed", it) }
    }

    private fun downloadArchive(repo: RepoSpec): File {
        val output = File.createTempFile("git-copy-", ".zip", context.cacheDir)
        val connection = (URL(repo.archiveUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Git-Copy-Android/0.1")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(240)
                    ?.ifBlank { null }
                error("GitHub returned HTTP $code${detail?.let { ": $it" } ?: ""}")
            }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(output).use { target -> input.copyTo(target) }
            }
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            connection.disconnect()
        }
        return output
    }

    private fun unzipRepository(
        archive: File,
        onFile: (relativePath: String, input: ZipInputStream) -> Unit,
    ) {
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val relative = stripArchiveRoot(entry.name)
                    if (relative.isNotBlank() && isSafeRelativePath(relative)) {
                        onFile(relative, zip)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun stripArchiveRoot(path: String): String =
        path.substringAfter('/', missingDelimiterValue = "")

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.startsWith('/') || path.startsWith('\\')) return false
        val parts = path.replace('\\', '/').split('/')
        return parts.none { it == ".." || it.isBlank() }
    }

    private fun writeToDownloads(repo: RepoSpec, relativePath: String, input: ZipInputStream) {
        val normalized = relativePath.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        val relativeDir = buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            append("/GitCopy/")
            append(repo.name)
            if (parent.isNotBlank()) {
                append('/')
                append(parent)
            }
            append('/')
        }

        val resolver = context.contentResolver
        deleteExistingOwnedDownload(resolver, relativeDir, fileName)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create $relativePath in Downloads")

        try {
            resolver.openOutputStream(uri, "w")?.use { input.copyTo(it) }
                ?: error("Could not open $relativePath for writing")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun deleteExistingOwnedDownload(
        resolver: ContentResolver,
        relativeDir: String,
        fileName: String,
    ) {
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(relativeDir, fileName),
        )
    }

    private fun writeToDocumentTree(root: DocumentFile, relativePath: String, input: ZipInputStream) {
        val parts = relativePath.replace('\\', '/').split('/')
        var parent = root
        for (directory in parts.dropLast(1)) {
            parent = parent.findFile(directory)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(directory)
                ?: error("Could not create folder $directory")
        }

        val fileName = parts.last()
        parent.findFile(fileName)?.delete()
        val file = parent.createFile("application/octet-stream", fileName)
            ?: error("Could not create $relativePath")
        context.contentResolver.openOutputStream(file.uri, "w")?.use { input.copyTo(it) }
            ?: error("Could not write $relativePath")
    }
}

data class CopyResult(
    val fileCount: Int,
    val destination: String,
)

class CopyException(message: String, cause: Throwable) : Exception(message, cause)
