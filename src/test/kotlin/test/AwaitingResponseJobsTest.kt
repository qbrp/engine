package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.mc.server.AwaitingResponseJobs

class AwaitingResponseJobsTest {
    @Test
    fun `job waits until its response is completed`() = runBlocking {
        val jobs = AwaitingResponseJobs<String, String>(this)
        var received: String? = null

        val job = requireNotNull(
            jobs.start("session") { response ->
                received = response.await()
            }
        )
        yield()

        assertTrue(job.isActive)
        assertNull(received)
        assertTrue(jobs.complete("session", "verified"))

        job.join()
        assertEquals("verified", received)
        assertFalse(jobs.isPending("session"))
    }

    @Test
    fun `only one job may wait for the same session`() = runBlocking {
        val jobs = AwaitingResponseJobs<String, String>(this)
        val firstJob = requireNotNull(jobs.start("session") { response -> response.await() })

        assertNull(jobs.start("session") { response -> response.await() })
        assertTrue(jobs.complete("session", "verified"))
        assertFalse(jobs.complete("session", "duplicate"))

        firstJob.join()
    }

    @Test
    fun `cancelled session can start a new job`() = runBlocking {
        val jobs = AwaitingResponseJobs<String, String>(this)
        val cancelledJob = requireNotNull(jobs.start("session") { response -> response.await() })

        jobs.cancel("session")
        cancelledJob.join()

        assertTrue(cancelledJob.isCancelled)
        assertFalse(jobs.isPending("session"))

        val replacementJob = requireNotNull(jobs.start("session") { response -> response.await() })
        jobs.cancel("session")
        replacementJob.join()
    }
}
