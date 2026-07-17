package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.data.network.SparkStatsItem
import com.securechat.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkStatsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.sparkStats.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSparkStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spark_stats_title), color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.spark_stats_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (stats == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val data = stats!!

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Zusammenfassung
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.spark_stats_overview),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SummaryStatItem(
                                icon = Icons.Default.Visibility,
                                label = stringResource(R.string.spark_stats_views),
                                value = data.totalViews.toString(),
                                tint = Color(0xFF1E88E5)
                            )
                            SummaryStatItem(
                                icon = Icons.Default.Favorite,
                                label = stringResource(R.string.spark_stats_likes),
                                value = data.totalLikes.toString(),
                                tint = Color(0xFFE53935)
                            )
                            SummaryStatItem(
                                icon = Icons.Default.People,
                                label = stringResource(R.string.spark_stats_subscribers),
                                value = data.totalSubscribers.toString(),
                                tint = Color(0xFF43A047)
                            )
                        }
                        if (data.avgWatchTime != null) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.spark_stats_avg_watchtime, String.format("%.1f", data.avgWatchTime)),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Ueberschrift pro Spark
            item {
                Text(
                    text = stringResource(R.string.spark_stats_per_spark),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (data.sparks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.spark_stats_no_sparks),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(data.sparks, key = { it.id }) { spark ->
                    SparkStatCard(spark = spark)
                }
            }
        }
    }
}

@Composable
private fun SparkStatCard(spark: SparkStatsItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = spark.title ?: "Spark",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparkStatChip(
                    icon = Icons.Default.Visibility,
                    value = spark.viewCount.toString(),
                    label = stringResource(R.string.spark_stats_views),
                    tint = Color(0xFF1E88E5),
                    modifier = Modifier.weight(1f)
                )
                SparkStatChip(
                    icon = Icons.Default.Favorite,
                    value = spark.likeCount.toString(),
                    label = stringResource(R.string.spark_stats_likes),
                    tint = Color(0xFFE53935),
                    modifier = Modifier.weight(1f)
                )
                SparkStatChip(
                    icon = Icons.Default.Loop,
                    value = spark.loopCount.toString(),
                    label = stringResource(R.string.spark_stats_loops),
                    tint = Color(0xFF7B1FA2),
                    modifier = Modifier.weight(1f)
                )
                SparkStatChip(
                    icon = Icons.Default.SkipNext,
                    value = spark.skipCount.toString(),
                    label = stringResource(R.string.spark_stats_skips),
                    tint = Color(0xFF757575),
                    modifier = Modifier.weight(1f)
                )
            }
            if (spark.avgDurationSeconds != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.spark_stats_avg_duration, String.format("%.1f", spark.avgDurationSeconds)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SparkStatChip(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = tint.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = tint
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryStatItem(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = tint)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
