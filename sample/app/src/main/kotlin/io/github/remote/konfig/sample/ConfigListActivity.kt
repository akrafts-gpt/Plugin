package io.github.remote.konfig.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.remote.konfig.RemoteConfigScreen
import javax.inject.Inject

@AndroidEntryPoint
class ConfigListActivity : AppCompatActivity() {

    @Inject
    lateinit var screens: Set<@JvmSuppressWildcards RemoteConfigScreen>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val entries = remember(screens) {
                        screens.map { ScreenEntry(it.id, it.title, it) }.sortedBy { it.title }
                    }
                    ConfigList(entries = entries) { entry ->
                        entry.screen.show(supportFragmentManager)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigList(entries: List<ScreenEntry>, onClick: (ScreenEntry) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries) { entry ->
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(entry) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Immutable
private data class ScreenEntry(
    val id: String,
    val title: String,
    val screen: RemoteConfigScreen,
)
