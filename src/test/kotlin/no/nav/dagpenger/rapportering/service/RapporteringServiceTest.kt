package no.nav.dagpenger.rapportering.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperioder
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Korrigert
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import no.nav.dagpenger.rapportering.utils.februar
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RapporteringServiceTest {
    private val meldepliktConnector = mockk<MeldepliktConnector>()
    private val rapporteringRepository = mockk<RapporteringRepository>()
    private val journalfoeringService = mockk<JournalfoeringService>()
    private val rapporteringService = RapporteringService(meldepliktConnector, rapporteringRepository, journalfoeringService)

    private val ident = "12345678910"
    private val token = "jwtToken"

    @Test
    fun `hent gjeldende periode henter kun ut eldste periode og lagrer denne i databasen hvis den ikke finnes`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        every { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        justRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        val gjeldendePeriode = runBlocking { rapporteringService.hentGjeldendePeriode(ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 1
            periode.fraOgMed shouldBe 1.januar
            periode.tilOgMed shouldBe 14.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 13.januar
            kanSendes shouldBe true
            kanKorrigeres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent gjeldende periode henter kun ut eldste periode som ikke er sendt inn fra før`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        every { rapporteringRepository.hentRapporteringsperiode(1L, ident) } returns
            lagRapporteringsperiode(
                id = 1,
                periode =
                    Periode(
                        fraOgMed = LocalDate.now().minusWeeks(3).plusDays(1),
                        tilOgMed = LocalDate.now().minusWeeks(1),
                    ),
                status = Innsendt,
            )
        every { rapporteringRepository.hentRapporteringsperiode(2L, ident) } returns null
        every { rapporteringRepository.hentRapporteringsperiode(3L, ident) } returns null
        justRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        val gjeldendePeriode = runBlocking { rapporteringService.hentGjeldendePeriode(ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            kanSendes shouldBe true
            kanKorrigeres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent gjeldende periode henter ut eldste periode og populerer denne med data fra databasen hvis den finnes`() {
        val rapporteringsperiodeFraDb =
            rapporteringsperiodeListe.first { it.id == 1L }.copy(
                dager =
                    getDager(
                        startDato = 1.januar,
                        aktivitet =
                            Aktivitet(
                                uuid = UUID.randomUUID(),
                                type = Arbeid,
                                timer = "PT5H30M",
                            ),
                    ),
            )
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        every { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns rapporteringsperiodeFraDb
        justRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        val gjeldendePeriode = runBlocking { rapporteringService.hentGjeldendePeriode(ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 1
            periode.fraOgMed shouldBe 1.januar
            periode.tilOgMed shouldBe 14.januar
            dager.size shouldBe 14
            dager.first().aktiviteter.first() shouldBe
                rapporteringsperiodeFraDb.dager
                    .first()
                    .aktiviteter
                    .first()
            kanSendesFra shouldBe 13.januar
            kanSendes shouldBe true
            kanKorrigeres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode som ikke er sendt inn og lagrer denne i databasen hvis den ikke finnes`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        every { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        justRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            kanSendes shouldBe true
            kanKorrigeres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode som er sendt inn`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns null
        coEvery { meldepliktConnector.hentInnsendteRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.map { it.copy(status = Innsendt) }.toAdapterRapporteringsperioder()

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            kanSendes shouldBe true
            kanKorrigeres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe Innsendt
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode og populerer denne med data fra databasen hvis den finnes`() {
        val rapporteringsperiodeFraDb =
            rapporteringsperiodeListe.first { it.id == 2L }.copy(
                dager =
                    getDager(
                        startDato = 14.januar,
                        aktivitet =
                            Aktivitet(
                                uuid = UUID.randomUUID(),
                                type = Utdanning,
                                timer = null,
                            ),
                    ),
            )
        coEvery { meldepliktConnector.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        every { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns rapporteringsperiodeFraDb
        justRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode(2L, ident, token) }

        with(gjeldendePeriode!!) {
            id shouldBe 2
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager shouldBe rapporteringsperiodeFraDb.dager
            kanSendesFra shouldBe 27.januar
            kanSendes shouldBe true
            kanKorrigeres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent alle rapporteringsperioder henter alle rapporteringsperioder og sorterer listen fra eldst til nyest`() {
        coEvery { meldepliktConnector.hentRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        every { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null

        val rapporteringsperioder = runBlocking { rapporteringService.hentAllePerioderSomKanSendes(ident, token)!! }

        rapporteringsperioder[0].id shouldBe 1L
        rapporteringsperioder[1].id shouldBe 2L
        rapporteringsperioder[2].id shouldBe 3L
        rapporteringsperioder.size shouldBe 3
    }

    @Test
    fun `kan lagre aktivitet på eksisterende rapporteringsperiode`() {
        val aktiviteter = listOf(Aktivitet(uuid = UUID.randomUUID(), type = Utdanning, timer = null))
        every { rapporteringRepository.hentDagId(any(), any()) } returns UUID.randomUUID()
        every { rapporteringRepository.hentAktiviteter(any()) } returns aktiviteter
        justRun { rapporteringRepository.slettAktiviteter(any()) }
        justRun { rapporteringRepository.lagreAktiviteter(any(), any(), any()) }

        rapporteringService.lagreEllerOppdaterAktiviteter(
            rapporteringId = 1L,
            dag =
                Dag(
                    dato = 1.januar,
                    aktiviteter = aktiviteter,
                    dagIndex = 0,
                ),
        )

        verify(exactly = 1) { rapporteringRepository.lagreAktiviteter(any(), any(), any()) }
        verify(exactly = 1) { rapporteringRepository.slettAktiviteter(any()) }
        verify(exactly = 1) { rapporteringRepository.hentDagId(any(), any()) }
        verify(exactly = 1) { rapporteringRepository.hentAktiviteter(any()) }
    }

    @Test
    fun `kan oppdatere om bruker vil fortsette som registrert arbeidssoker`() {
        justRun { rapporteringRepository.oppdaterRegistrertArbeidssoker(any(), any(), any()) }

        rapporteringService.oppdaterRegistrertArbeidssoker(1L, "12345678910", true)

        verify(exactly = 1) { rapporteringRepository.oppdaterRegistrertArbeidssoker(1L, "12345678910", true) }
    }

    @Test
    fun `kan korrigere meldekort`() {
        every { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns rapporteringsperiodeListe.first()
        coEvery { meldepliktConnector.hentKorrigeringId(any(), any()) } returns 321L
        justRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        val korrigertRapporteringsperiode = runBlocking { rapporteringService.korrigerMeldekort(123L, ident, token) }

        korrigertRapporteringsperiode.id shouldBe 321L
        korrigertRapporteringsperiode.status shouldBe Korrigert
    }

    @Test
    fun `kan hente innsendte rapporteringsperioder`() {
        coEvery {
            meldepliktConnector.hentInnsendteRapporteringsperioder(any(), any())
        } returns
            rapporteringsperiodeListe
                .map { it.copy(status = Innsendt, kanSendes = false, kanKorrigeres = true) }
                .toAdapterRapporteringsperioder()

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token) }

        innsendteRapporteringsperioder.size shouldBe 3
        innsendteRapporteringsperioder[0].id shouldBe 3L
        innsendteRapporteringsperioder[1].id shouldBe 2L
        innsendteRapporteringsperioder[2].id shouldBe 1L
    }

    @Test
    fun `kan sende inn rapporteringsperiode`() {
        val rapporteringsperiode = rapporteringsperiodeListe.first()
        coEvery { journalfoeringService.journalfoer(any(), any(), any()) } returns mockk()
        justRun { rapporteringRepository.oppdaterRapporteringStatus(any(), any(), any()) }
        coEvery { meldepliktConnector.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = rapporteringsperiode.id,
                status = "OK",
                feil = listOf(),
            )

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, 4)
            }

        innsendingResponse.id shouldBe rapporteringsperiode.id
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any())
            }
        }
        verify(exactly = 1) { rapporteringRepository.oppdaterRapporteringStatus(any(), any(), any()) }
    }

    @Test
    fun `lagreEllerOppdaterPeriode lagrer perioden hvis den ikke finnes i databasen`() {
        every { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        justRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident)

        verify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
    }

    @Test
    fun `lagreEllerOppdaterPeriode oppdaterer perioden hvis den finnes i databasen fra før`() {
        every { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns rapporteringsperiodeListe.first()
        justRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident)

        verify(exactly = 1) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
    }

    @Test
    fun `lagreEllerOppdaterPeriode oppdaterer ikke perioden hvis den finnes i databasen fra før med en høyere status`() {
        every { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns
            rapporteringsperiodeListe.first().copy(status = Innsendt)

        rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident)

        verify(exactly = 0) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er sendt inn`() {
        every { rapporteringRepository.hentRapporteringsperioder() } returns
            listOf(rapporteringsperiodeListe.first().copy(status = Innsendt))
        justRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        rapporteringService.slettMellomlagredeRapporteringsperioder()

        verify(exactly = 1) { rapporteringRepository.slettRaporteringsperiode(3) }
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er ikke er sendt inn innen siste frist`() {
        every { rapporteringRepository.hentRapporteringsperioder() } returns rapporteringsperiodeListe
        justRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        rapporteringService.slettMellomlagredeRapporteringsperioder()

        verify(exactly = 3) { rapporteringRepository.slettRaporteringsperiode(any()) }
    }

    @Test
    fun `sletter ikke mellomlagrede rapporteringsperioder som er fortsatt skal være mellomlagret`() {
        every { rapporteringRepository.hentRapporteringsperioder() } returns fremtidigeRaporteringsperioder

        rapporteringService.slettMellomlagredeRapporteringsperioder()

        verify(exactly = 0) { rapporteringRepository.slettRaporteringsperiode(any()) }
    }
}

val rapporteringsperiodeListe =
    listOf(
        lagRapporteringsperiode(
            id = 3,
            periode = Periode(fraOgMed = 29.januar, tilOgMed = 11.februar),
        ),
        lagRapporteringsperiode(
            id = 1,
            periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
        ),
        lagRapporteringsperiode(
            id = 2,
            periode = Periode(fraOgMed = 15.januar, tilOgMed = 28.januar),
        ),
    )

val fremtidigeRaporteringsperioder =
    listOf(
        lagRapporteringsperiode(
            id = 1,
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(3).plusDays(1),
                    tilOgMed = LocalDate.now().minusWeeks(1),
                ),
        ),
        lagRapporteringsperiode(
            id = 2,
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(2),
                    tilOgMed = LocalDate.now().minusDays(1),
                ),
        ),
        lagRapporteringsperiode(
            id = 3,
            periode =
                Periode(
                    fraOgMed = LocalDate.now(),
                    tilOgMed = LocalDate.now().plusWeeks(2).minusDays(1),
                ),
        ),
    )

fun lagRapporteringsperiode(
    id: Long,
    periode: Periode,
    status: RapporteringsperiodeStatus = TilUtfylling,
) = Rapporteringsperiode(
    id = id,
    periode = periode,
    dager = getDager(startDato = periode.fraOgMed),
    kanSendesFra = periode.tilOgMed.minusDays(1),
    kanSendes = true,
    kanKorrigeres = false,
    bruttoBelop = null,
    status = status,
    registrertArbeidssoker = null,
)

private fun getDager(
    startDato: LocalDate = 1.januar,
    aktivitet: Aktivitet? = null,
): List<Dag> =
    (0..13)
        .map { i ->
            Dag(
                dato = startDato.plusDays(i.toLong()),
                aktiviteter = aktivitet?.let { listOf(it) } ?: emptyList(),
                dagIndex = i,
            )
        }
