package no.nav.dagpenger.rapportering.meldinger

import java.util.UUID

internal interface HendelseRepository {
    fun lagreMelding(
        hendelseMessage: HendelseMessage,
        ident: String,
        id: UUID,
        toJson: String,
    )
}
