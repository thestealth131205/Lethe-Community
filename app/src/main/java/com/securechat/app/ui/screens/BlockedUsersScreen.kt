package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.data.network.BlockedUserResponse
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    var confirmUnblockUser by remember { mutableStateOf<BlockedUserResponse?>(null) }

    LaunchedEffect(Unit) { viewModel.loadBlockedUsers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_users_title), color = topBarTitleColor()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.blocked_users_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (blockedUsers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.blocked_users_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(blockedUsers, key = { it.id }) { user ->
                    BlockedUserItem(
                        user = user,
                        onUnblock = { confirmUnblockUser = user }
                    )
                }
            }
        }
    }

    confirmUnblockUser?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmUnblockUser = null },
            title = { Text(stringResource(R.string.blocked_users_unblock_dialog)) },
            text = {
                Text("${user.blockedName ?: user.blockedFakeNumber ?: "Nutzer"} von der Blockliste entfernen?")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unblockUser(user.blockedId)
                    confirmUnblockUser = null
                }) { Text(stringResource(R.string.blocked_users_unblock_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnblockUser = null }) {
                    Text(stringResource(R.string.blocked_users_unblock_cancel))
                }
            }
        )
    }
}

@Composable
private fun BlockedUserItem(
    user: BlockedUserResponse,
    onUnblock: () -> Unit
) {
    val avatarShape = RoundedCornerShape(10.dp)
    ListItem(
        headlineContent = {
            Text(user.blockedName ?: user.blockedFakeNumber ?: "Unbekannt")
        },
        supportingContent = user.blockedFakeNumber?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        leadingContent = {
            if (user.blockedImageUrl != null) {
                AsyncImage(
                    model = user.blockedImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(avatarShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = avatarShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person, null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onUnblock) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = stringResource(R.string.blocked_users_unblock_button),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
    HorizontalDivider(Modifier.padding(start = 72.dp))
}
