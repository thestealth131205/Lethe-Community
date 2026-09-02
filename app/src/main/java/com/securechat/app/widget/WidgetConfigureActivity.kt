package com.securechat.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.securechat.app.R
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.local.GroupDao
import com.securechat.app.data.local.GroupEntity
import com.securechat.app.ui.theme.SecureChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Konfigurations-Bildschirm für das Startbildschirm-Widget: der Nutzer wählt hier,
 * auf welchen Kontakt oder welche Gruppe das neu platzierte Widget zeigen soll.
 * Wird vom System automatisch beim Hinzufügen des Widgets gestartet (siehe
 * res/xml/contact_widget_info.xml, android:configure).
 */
@AndroidEntryPoint
class WidgetConfigureActivity : ComponentActivity() {

    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var groupDao: GroupDao

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Falls der Nutzer abbricht (Zurück-Taste), keine Widget-Platzierung übernehmen.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            SecureChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WidgetPickerScreen(
                        contactDao = contactDao,
                        groupDao = groupDao,
                        onPicked = { isGroup, id -> finishWithSelection(isGroup, id) }
                    )
                }
            }
        }
    }

    private fun finishWithSelection(isGroup: Boolean, targetId: String) {
        ContactWidgetProvider.saveWidgetTarget(this, appWidgetId, isGroup, targetId)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        lifecycleScope.launch {
            ContactWidgetProvider.updateWidgetInternal(
                this@WidgetConfigureActivity, appWidgetManager, appWidgetId, contactDao, groupDao
            )
        }

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

private sealed class WidgetPickItem {
    data class Contact(val entity: ContactEntity) : WidgetPickItem()
    data class Group(val entity: GroupEntity) : WidgetPickItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPickerScreen(
    contactDao: ContactDao,
    groupDao: GroupDao,
    onPicked: (isGroup: Boolean, targetId: String) -> Unit
) {
    var items by remember { mutableStateOf<List<WidgetPickItem>?>(null) }

    LaunchedEffect(Unit) {
        val contacts = contactDao.getAllContacts().first()
            .filter { it.status == "accepted" }
            .map { WidgetPickItem.Contact(it) }
        val groups = groupDao.getAllGroups().first()
            .map { WidgetPickItem.Group(it) }
        items = contacts + groups
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.widget_picker_title)) })
        }
    ) { padding ->
        val currentItems = items
        if (currentItems == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.widget_picker_empty))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(currentItems) { item ->
                    when (item) {
                        is WidgetPickItem.Contact -> {
                            val name = item.entity.customAlias ?: item.entity.username ?: item.entity.fakeNumber
                            WidgetPickRow(
                                name = name,
                                imageUrl = item.entity.profileImageUrl,
                                fallbackIcon = Icons.Default.Person,
                                onClick = { onPicked(false, item.entity.userId) }
                            )
                        }
                        is WidgetPickItem.Group -> {
                            WidgetPickRow(
                                name = item.entity.name,
                                imageUrl = item.entity.groupImageUrl,
                                fallbackIcon = Icons.Default.Group,
                                onClick = { onPicked(true, item.entity.groupId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPickRow(
    name: String,
    imageUrl: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val absoluteUrl = imageUrl?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" }
    ListItem(
        headlineContent = { Text(name) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (absoluteUrl != null) {
                    coil.compose.AsyncImage(
                        model = absoluteUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(fallbackIcon, contentDescription = null)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}
