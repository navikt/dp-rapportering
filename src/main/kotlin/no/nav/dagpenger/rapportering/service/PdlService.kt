package no.nav.dagpenger.rapportering.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.pdl.PersonOppslag
import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.rapportering.config.Configuration.pdlApiTokenProvider
import no.nav.dagpenger.rapportering.config.Configuration.pdlUrl
import no.nav.dagpenger.rapportering.utils.Sikkerlogg

class PdlService(
    val personOppslag: PersonOppslag = createPersonOppslag(pdlUrl),
    val tokenProvider: () -> String? = pdlApiTokenProvider,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun hentNavn(ident: String): String =
        withContext(Dispatchers.IO) {
            try {
                val person =
                    personOppslag
                        .hentPerson(
                            ident,
                            mapOf(
                                HttpHeaders.Authorization to
                                    "Bearer ${tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")}",
                                // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
                                "behandlingsnummer" to "B286",
                            ),
                        )

                val mellomnavn = person.mellomnavn?.let { "$it " } ?: ""
                person.fornavn + " " + mellomnavn + person.etternavn
            } catch (e: Exception) {
                logger.error(e) { "Feil ved henting av person fra PDL" }
                Sikkerlogg.error(e) { "Feil ved henting av person fra PDL for ident $ident" }
                ""
            }
        }
}
