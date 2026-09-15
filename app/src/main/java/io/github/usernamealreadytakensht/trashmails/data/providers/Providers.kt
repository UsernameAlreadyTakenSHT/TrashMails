package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MessageCache
import io.github.usernamealreadytakensht.trashmails.data.Prefs
import io.github.usernamealreadytakensht.trashmails.data.Provider

/**
 * One implementation per [Provider], built here and nowhere else. The `when` is exhaustive: a new
 * enum entry does not compile until its implementation is listed, instead of failing at runtime.
 * [prefsFor] opens a named preference file for the providers that keep state of their own.
 */
fun allProviders(http: HttpApi = Http, cache: MessageCache, prefsFor: (String) -> Prefs): Map<Provider, MailProvider> =
    Provider.entries.associateWith { provider ->
        when (provider) {
            Provider.INBOX_KITTEN -> InboxKittenProvider(http)
            Provider.MAILDROP -> MaildropProvider(http)
            Provider.GUERRILLA_MAIL -> GuerrillaMailProvider(http)
            Provider.MAIL_TM -> MailTmProvider(http)
            Provider.BURNER_KIWI -> BurnerKiwiProvider(http)
            Provider.TEMPMAIL_LOL -> TempmailLolProvider(http, cache)
            Provider.DROPMAIL -> DropMailProvider(http, cache, prefsFor("dropmail"))
        }
    }
