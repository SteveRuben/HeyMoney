package org.abodah.heymoney.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class NewsState(
    val loading: Boolean = true,
    val items: List<InvestmentNews> = emptyList(),
    val failed: Boolean = false,
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val service: InvestmentNewsService
) : ViewModel() {

    private val _state = MutableStateFlow(NewsState())
    val state: StateFlow<NewsState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            val items = runCatching { service.fetchNewsFeed() }.getOrDefault(emptyList())
            _state.value = if (items.isEmpty() && _state.value.items.isEmpty()) {
                NewsState(loading = false, failed = true)
            } else {
                NewsState(loading = false, items = items.ifEmpty { _state.value.items })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(viewModel: NewsViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    var filter by remember { mutableStateOf<String?>(null) }
    val types = state.items.map { it.type }.distinct()
    val shown = state.items.filter { filter == null || it.type == filter }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Investment news") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (types.size > 1) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                    types.forEach { type ->
                        FilterChip(
                            selected = filter == type,
                            onClick = { filter = type },
                            label = { Text(type) }
                        )
                    }
                }
            }

            when {
                state.loading && state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }

                state.failed -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Could not load the news. Check your connection and refresh.")
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shown, key = { it.url + it.title }) { news ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = news.url.isNotBlank()) { uriHandler.openUri(news.url) }
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${news.type} • ${news.source} • " +
                                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(news.publishedAt)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(news.title, fontWeight = FontWeight.SemiBold)
                                if (news.summary.isNotBlank() && news.summary != news.title) {
                                    Text(
                                        news.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
