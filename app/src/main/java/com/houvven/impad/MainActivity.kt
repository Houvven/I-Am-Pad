package com.houvven.impad

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.houvven.impad.ui.theme.AppTheme
import kotlinx.coroutines.launch

private val snackbarHostState = SnackbarHostState()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { AppTopBar() },
                    snackbarHost = {
                        SnackbarHost(snackbarHostState)
                    }
                ) { innerPadding ->
                    App(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun App(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.clear_cache_tips),
                modifier = Modifier.padding(16.dp)
            )
        }
        listOf(QQ_PACKAGE_NAME, WECHAT_PACKAGE_NAME).forEach { packageName ->
            AppCard(packageName) {
                context.dataChannel(packageName).put(ClearCacheKey)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar() {
    TopAppBar(
        title = { Text(text = stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = {}) {
                Image(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    )
}

@Composable
private fun AppCard(packageName: String, onClick: () -> Unit) {
    val pm = LocalContext.current.packageManager
    val appInfo = pm.getApplicationInfo(packageName, 0)
    val appIcon = pm.getApplicationIcon(appInfo).toBitmap().asImageBitmap()
    val appName = pm.getApplicationLabel(appInfo).toString()
    var lastClickTime by remember(packageName) { mutableLongStateOf(0L) }

    ListItem(
        headlineContent = {
            Text(text = appName)
        },
        supportingContent = {
            Text(text = packageName)
        },
        leadingContent = {
            Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(40.dp))
        },
        trailingContent = {
            IconButton(onClick = {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime > 300) {
                    lastClickTime = currentTime
                    onClick.invoke()
                }
            }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppTheme {
        App()
    }
}