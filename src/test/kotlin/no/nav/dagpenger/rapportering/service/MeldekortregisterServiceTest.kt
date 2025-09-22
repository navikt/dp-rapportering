package no.nav.dagpenger.rapportering.service

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.api.objectMapper
import no.nav.dagpenger.rapportering.connector.createMockClient
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.PeriodeData
import no.nav.dagpenger.rapportering.model.PeriodeData.Kilde
import no.nav.dagpenger.rapportering.model.PeriodeData.OpprettetAv
import no.nav.dagpenger.rapportering.model.PeriodeData.PeriodeDag
import no.nav.dagpenger.rapportering.model.PeriodeData.Type
import no.nav.dagpenger.rapportering.model.toKorrigerMeldekortHendelse
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class MeldekortregisterServiceTest {
    private val testTokenProvider: (token: String) -> String = { _ -> "testToken" }
    private val meldekortregisterUrl = "http://meldekortregisterUrl"
    private val token = "gylidg_token"
    private val ident = "12345678903"
    private val rapporteringId = "1806478069"

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    private fun meldekortregisterService(
        statusCode: HttpStatusCode,
        responseBody: String = "",
    ) = MeldekortregisterService(
        meldekortregisterUrl = meldekortregisterUrl,
        tokenProvider = testTokenProvider,
        httpClient = createMockClient(statusCode, responseBody),
        actionTimer = actionTimer,
    )

    @Test
    fun `kan hente meldekort`() {
        val periode1 = Periode(LocalDate.now(), LocalDate.now().plusDays(13))
        val periode2 = Periode(LocalDate.now().minusDays(14), LocalDate.now().minusDays(1))

        val aktivitet1 =
            Aktivitet(
                UUID.randomUUID(),
                Aktivitet.AktivitetsType.Arbeid,
                "5.5",
            )
        val aktivitet2 =
            Aktivitet(
                UUID.randomUUID(),
                Aktivitet.AktivitetsType.Utdanning,
                "",
            )

        val periodeDataList =
            listOf(
                PeriodeData(
                    id = "1",
                    ident = "01020312345",
                    periode = periode1,
                    dager = emptyList(),
                    kanSendesFra = LocalDate.now(),
                    opprettetAv = OpprettetAv.Dagpenger,
                    kilde = Kilde(PeriodeData.Rolle.Bruker, "01020312345"),
                    type = Type.Original,
                    status = "TilUtfylling",
                    innsendtTidspunkt = null,
                    korrigeringAv = null,
                    bruttoBelop = null,
                    begrunnelseEndring = null,
                    registrertArbeidssoker = null,
                ),
                PeriodeData(
                    id = "2",
                    ident = "01020312346",
                    periode = periode2,
                    dager =
                        listOf(
                            PeriodeDag(
                                LocalDate.now(),
                                listOf(aktivitet1),
                                0,
                            ),
                            PeriodeDag(
                                LocalDate.now().plusDays(1),
                                listOf(aktivitet2),
                                1,
                            ),
                        ),
                    kanSendesFra = LocalDate.now(),
                    opprettetAv = OpprettetAv.Arena,
                    kilde = Kilde(PeriodeData.Rolle.Bruker, "01020312346"),
                    type = Type.Korrigert,
                    status = "Endret",
                    innsendtTidspunkt = LocalDateTime.now(),
                    korrigeringAv = "123456789",
                    bruttoBelop = 123.0,
                    begrunnelseEndring = "Begrunnelse",
                    registrertArbeidssoker = true,
                ),
            )

        // OK
        var meldekortregisterService =
            meldekortregisterService(HttpStatusCode.OK, objectMapper().writeValueAsString(periodeDataList))

        var response =
            runBlocking {
                meldekortregisterService.hentRapporteringsperioder(ident, token)
            }

        response shouldBe periodeDataList

        // NoContent
        meldekortregisterService = meldekortregisterService(HttpStatusCode.NoContent)

        response =
            runBlocking {
                meldekortregisterService.hentRapporteringsperioder(ident, token)
            }

        response shouldBe null
    }

    @Test
    fun `kan hente endringId`() {
        val meldekortregisterService = meldekortregisterService(HttpStatusCode.OK, "124")

        val response =
            runBlocking {
                meldekortregisterService.hentEndringId("123", token)
            }

        response shouldBe "124"
    }

    @Test
    fun `kan sende inn meldekort`() {
        // OK
        var meldekortregisterService = meldekortregisterService(HttpStatusCode.OK)

        val id = "123456789"
        val periode = Periode(LocalDate.now(), LocalDate.now().plusDays(13))

        val periodeData =
            PeriodeData(
                id = id,
                ident = "01020312345",
                periode = periode,
                dager =
                    (0..13)
                        .map { i ->
                            PeriodeDag(
                                dato = LocalDate.now().plusDays(i.toLong()),
                                aktiviteter = listOf(Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Utdanning, "")),
                                dagIndex = i,
                            )
                        },
                kanSendesFra = LocalDate.now(),
                opprettetAv = OpprettetAv.Dagpenger,
                kilde = Kilde(PeriodeData.Rolle.Bruker, "01020312345"),
                type = Type.Korrigert,
                status = "TilInnsending",
                innsendtTidspunkt = LocalDateTime.now(),
                korrigeringAv = "123456788",
                bruttoBelop = null,
                begrunnelseEndring = "Begrunnelse",
                registrertArbeidssoker = true,
            )

        var response =
            runBlocking {
                meldekortregisterService.sendinnRapporteringsperiode(periodeData, token)
            }

        response shouldBe InnsendingResponse(id, "OK", emptyList())

        // Feil
        meldekortregisterService = meldekortregisterService(HttpStatusCode.InternalServerError)

        response =
            runBlocking {
                meldekortregisterService.sendinnRapporteringsperiode(periodeData, token)
            }

        response shouldBe InnsendingResponse(id, "FEIL", emptyList())
    }

    @Test
    fun `kan sende inn korrigering av meldekort`() {
        // OK
        var meldekortregisterService = meldekortregisterService(HttpStatusCode.OK)

        val originalId = "123456789"
        val periode = Periode(LocalDate.now(), LocalDate.now().plusDays(13))

        val korrigertMeldekortHendelse =
            PeriodeData(
                id = "123456788",
                ident = "01020312345",
                periode = periode,
                dager =
                    (0..13)
                        .map { i ->
                            PeriodeDag(
                                dato = LocalDate.now().plusDays(i.toLong()),
                                aktiviteter = listOf(Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Utdanning, "")),
                                dagIndex = i,
                            )
                        },
                kanSendesFra = LocalDate.now(),
                opprettetAv = OpprettetAv.Dagpenger,
                kilde = Kilde(PeriodeData.Rolle.Bruker, "01020312345"),
                type = Type.Korrigert,
                status = "TilInnsending",
                innsendtTidspunkt = LocalDateTime.now(),
                korrigeringAv = originalId,
                bruttoBelop = null,
                begrunnelseEndring = "Begrunnelse",
                registrertArbeidssoker = true,
            ).toKorrigerMeldekortHendelse()

        var response =
            runBlocking {
                meldekortregisterService.sendKorrigertMeldekort(korrigertMeldekortHendelse, token)
            }

        response shouldBe InnsendingResponse(originalId, "OK", emptyList())

        // Feil
        meldekortregisterService = meldekortregisterService(HttpStatusCode.InternalServerError)

        response =
            runBlocking {
                meldekortregisterService.sendKorrigertMeldekort(korrigertMeldekortHendelse, token)
            }

        response shouldBe InnsendingResponse(originalId, "FEIL", emptyList())
    }
}
