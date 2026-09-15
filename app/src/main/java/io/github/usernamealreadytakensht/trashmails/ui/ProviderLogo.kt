package io.github.usernamealreadytakensht.trashmails.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.usernamealreadytakensht.trashmails.R
import io.github.usernamealreadytakensht.trashmails.data.Provider

@DrawableRes
fun providerLogo(provider: Provider): Int = when (provider) {
    Provider.INBOX_KITTEN -> R.drawable.logo_inbox_kitten
    Provider.GUERRILLA_MAIL -> R.drawable.logo_guerrilla_mail
    Provider.MAIL_TM -> R.drawable.logo_mail_tm
    Provider.BURNER_KIWI -> R.drawable.logo_burner_kiwi
    Provider.MAILDROP -> R.drawable.logo_maildrop
    Provider.TEMPMAIL_LOL -> R.drawable.logo_tempmail_lol
}

/**
 * Provider logo in a white rounded tile, scaled to fit. [described] false when the provider name
 * is written next to it, so screen readers do not announce it twice.
 */
@Composable
fun ProviderLogo(provider: Provider, size: Dp, modifier: Modifier = Modifier, described: Boolean = true) {
    Image(
        painter = painterResource(providerLogo(provider)),
        contentDescription = if (described) provider.label else null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(4.dp),
    )
}
