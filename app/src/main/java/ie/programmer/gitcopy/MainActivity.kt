package ie.programmer.gitcopy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GitCopyScreen()
                }
            }
        }
    }
}

@Composable
private fun GitCopyScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val copier = remember { RepositoryCopier(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var repoUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Paste a public GitHub repository URL to begin.") }
    var busy by remember { mutableStateOf(false) }

    fun parsedRepo(): RepoSpec? {
        val repo = RepoSpec.parse(repoUrl)
        if (repo == null) status = "Use a URL like https://github.com/owner/repository"
        return repo
    }

    fun runCopy(block: suspend (RepoSpec, (String) -> Unit) -> CopyResult) {
        val repo = parsedRepo() ?: return
        busy = true
        scope.launch {
            try {
                val result = block(repo) { progress -> status = progress }
                status = "Done — ${result.fileCount} files copied to ${result.destination}."
            } catch (t: Throwable) {
                status = "Copy failed: ${t.message ?: "unknown error"}"
            } finally {
                busy = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            runCopy { repo, progress -> copier.copyToTree(repo, uri, progress) }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "Git Copy",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Copy a GitHub repository snapshot onto this Android device.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = repoUrl,
                onValueChange = { repoUrl = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                label = { Text("GitHub repository URL") },
                placeholder = { Text("https://github.com/owner/repo") },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    runCopy { repo, progress -> copier.copyToDownloads(repo, progress) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && repoUrl.isNotBlank(),
            ) {
                Text("Copy to Downloads")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { if (parsedRepo() != null) folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && repoUrl.isNotBlank(),
            ) {
                Text("Choose another folder")
            }
            Spacer(Modifier.height(28.dp))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(14.dp))
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Starter limitation: this copies the current public repository snapshot, not Git history or private repositories.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
