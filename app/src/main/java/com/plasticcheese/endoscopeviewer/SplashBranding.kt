package com.plasticcheese.endoscopeviewer

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun SplashBranding() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val website = stringResource(R.string.website_url)

    Image(
        painter = painterResource(R.drawable.plasticcheese),
        contentDescription = null,
        modifier = Modifier.size(112.dp),
    )
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = {
        try {
            uriHandler.openUri(website)
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, R.string.website_open_failed, Toast.LENGTH_SHORT).show()
        }
    }) {
        Text(stringResource(R.string.website_label))
    }
}
