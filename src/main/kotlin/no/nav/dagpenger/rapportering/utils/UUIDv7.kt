package no.nav.dagpenger.rapportering.utils

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.UUIDUtil
import java.util.UUID

object UUIDv7 {
    private val idGenerator = Generators.timeBasedEpochGenerator()

    fun newUuid(): UUID = idGenerator.generate()

    fun fromString(uuid: String): UUID = UUIDUtil.uuid(uuid)
}
