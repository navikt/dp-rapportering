package no.nav.dagpenger.rapportering.db

import no.nav.dagpenger.rapportering.db.Postgres.withCleanDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.runMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgresMigrationTest {
    @Test
    fun `Migration scripts are applied successfully`() {
        withCleanDb {
            val migrations = runMigration()
            assertEquals(5, migrations)
        }
    }
}
