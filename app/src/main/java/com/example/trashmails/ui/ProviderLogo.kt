package com.example.trashmails.ui

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
import com.example.trashmails.R
import com.example.trashmails.data.Provider

@DrawableRes
fun providerLogo(provider: Provider): Int = when (provider) {
    Provider.INBOX_KITTEN -> R.drawable.logo_inbox_kitten
    Provider.GUERRILLA_MAIL -> R.drawable.logo_guerrilla_mail
    Provider.MAIL_TM -> R.drawable.logo_mail_tm
    Provider.BURNER_KIWI -> R.drawable.logo_burner_kiwi
    Provider.MAILDROP -> R.drawable.logo_maildrop
}

/** Provider logo in a white rounded tile, scaled to fit. */
@Composable
fun ProviderLogo(provider: Provider, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(providerLogo(provider)),
        contentDescription = provider.label,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(4.dp),
    )
}
