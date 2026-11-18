package com.plasticcheese.endoscopeviewer

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@Composable
internal fun SplashBranding(imageModifier: Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val website = stringResource(R.string.website_url)

    Text(
        text = stringResource(R.string.welcome_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Image(
        painter = painterResource(R.drawable.plasticcheese),
        contentDescription = null,
        modifier = imageModifier,
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
