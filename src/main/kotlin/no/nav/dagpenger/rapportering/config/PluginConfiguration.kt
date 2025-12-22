package no.nav.dagpenger.rapportering.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.doublereceive.DoubleReceive
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.azureAd
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.tokenX
import no.nav.dagpenger.rapportering.repository.KallLoggRepository
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.utils.IncomingCallLoggingPlugin

fun Application.pluginConfiguration(
    kallLoggRepository: KallLoggRepository,
    auth: AuthenticationConfig.() -> Unit = {
        jwt("tokenX") { tokenX() }
        jwt("azureAd") { azureAd() }
    },
) {
    runMigration()

    install(DoubleReceive) {
    }

    install(IncomingCallLoggingPlugin) {
        this.kallLoggRepository = kallLoggRepository
    }

    install(Authentication) {
        auth()
    }
}
