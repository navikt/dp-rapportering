package no.nav.dagpenger.rapportering.model.hendelse

import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

data class InnsendtPeriodeHendelse(
    val meldingsreferanseId: UUID = UUID.randomUUID(),
    val ident: String,
    val rapporteringsperiode: Rapporteringsperiode,
) : PersonHendelse(meldingsreferanseId, ident)

class MeldingOmPeriodeInnsendt(
    private val innsendtPeriodeHendelse: InnsendtPeriodeHendelse,
) {
    fun asMessage(): JsonMessage =
        JsonMessage.newMessage(
            eventName = "rapporteringsperiode_innsendt_hendelse",
            map =
                mapOf(
                    "ident" to innsendtPeriodeHendelse.ident,
                    "rapporteringsperiodeId" to innsendtPeriodeHendelse.rapporteringsperiode.id,
                    "fom" to innsendtPeriodeHendelse.rapporteringsperiode.periode.fraOgMed,
                    "tom" to innsendtPeriodeHendelse.rapporteringsperiode.periode.tilOgMed,
                    "dager" to
                        innsendtPeriodeHendelse.rapporteringsperiode.dager.map { dag ->
                            mapOf(
                                "dato" to dag.dato,
                                "dagIndex" to dag.dagIndex,
                                "aktiviteter" to
                                    dag.aktiviteter.map { aktivitet ->
                                        mapOf(
                                            "id" to aktivitet.id,
                                            "type" to aktivitet.type,
                                            "timer" to aktivitet.timer,
                                        )
                                    },
                            )
                        },
                ),
        )
}

class MeldingOmArbeidssokerNestePeriode(
    private val innsendtPeriodeHendelse: InnsendtPeriodeHendelse,
) {
    fun asMessage(): JsonMessage =
        JsonMessage.newMessage(
            eventName = "arbeidssoker_neste_periode_hendelse",
            map =
                mapOf(
                    "ident" to innsendtPeriodeHendelse.ident,
                    "fom" to innsendtPeriodeHendelse.rapporteringsperiode.kanSendesFra.plusDays(1),
                    "tom" to innsendtPeriodeHendelse.rapporteringsperiode.kanSendesFra.plusDays(14),
                    "registrertArbeidssoker" to innsendtPeriodeHendelse.rapporteringsperiode.registrertArbeidssoker!!,
                ),
        )
}
