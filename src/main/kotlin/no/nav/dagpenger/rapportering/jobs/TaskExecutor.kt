package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

interface Task {
    fun execute()
}

data class ScheduledTask(
    val task: Task,
    val hour: Int,
    val minute: Int,
) {
    init {
        require(hour in 0..23) { "hour must be in range 0..23, but was $hour" }
        require(minute in 0..59) { "minute must be in range 0..59, but was $minute" }
    }
}

class TaskExecutor(
    private val scheduledTasks: List<ScheduledTask>,
) {
    val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(scheduledTasks.size)

    fun startExecution() {
        scheduledTasks.forEach { scheduledTask -> startExecution(scheduledTask) }
    }

    fun startExecution(scheduledTask: ScheduledTask) {
        val taskWrapper =
            Runnable {
                try {
                    scheduledTask.task.execute()
                } catch (e: Exception) {
                    logger.error(e) { "Planlagte oppgaven ${scheduledTask.task} feilet" }
                }
                startExecution(scheduledTask)
            }
        val delay = computeNextDelay(scheduledTask.hour, scheduledTask.minute)
        executorService.schedule(taskWrapper, delay, TimeUnit.SECONDS)
    }

    private fun computeNextDelay(
        hour: Int,
        minute: Int,
    ): Long {
        val localNow = LocalDateTime.now()
        val currentZone = ZoneId.systemDefault()
        val zonedNow = ZonedDateTime.of(localNow, currentZone)
        var zonedNextTarget =
            zonedNow
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
        if (zonedNow > zonedNextTarget) zonedNextTarget = zonedNextTarget.plusDays(1)

        val duration = Duration.between(zonedNow, zonedNextTarget)
        return duration.seconds
    }
}
