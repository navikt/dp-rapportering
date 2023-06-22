package no.nav.dagpenger.rapportering.hendelser
import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDateTime
import java.util.UUID

class SøknadInnsendtHendelse(meldingsreferanseId: UUID, ident: String, internal val opprettet: LocalDateTime) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
)
