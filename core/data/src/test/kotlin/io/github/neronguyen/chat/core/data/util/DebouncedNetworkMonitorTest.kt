package io.github.neronguyen.chat.core.data.util

import io.github.neronguyen.chat.core.data.testing.TestNetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedNetworkMonitorTest {

    @Test
    fun `cold start online emits true immediately without delay`() = runTest {
        val delegate = TestNetworkMonitor(initialOnline = true)
        val monitor = DebouncedNetworkMonitor(delegate = delegate, appScope = backgroundScope)

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(emissions::add)
        }
        testScheduler.runCurrent()

        assertEquals(listOf(true), emissions)
        job.cancel()
    }

    @Test
    fun `cold start offline emits false immediately without delay`() = runTest {
        val delegate = TestNetworkMonitor(initialOnline = false)
        val monitor = DebouncedNetworkMonitor(delegate = delegate, appScope = backgroundScope)

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(emissions::add)
        }
        testScheduler.runCurrent()

        assertEquals(listOf(false), emissions)
        job.cancel()
    }

    @Test
    fun `true to false transition is debounced by timeout`() = runTest {
        val delegate = TestNetworkMonitor(initialOnline = true)
        val monitor = DebouncedNetworkMonitor(delegate = delegate, appScope = backgroundScope)

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(emissions::add)
        }
        testScheduler.runCurrent()
        assertEquals(listOf(true), emissions)

        delegate.setConnected(false)
        testScheduler.advanceTimeBy(299)
        testScheduler.runCurrent()
        assertEquals(listOf(true), emissions)

        testScheduler.advanceTimeBy(1)
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), emissions)
        job.cancel()
    }

    @Test
    fun `transient offline blip shorter than timeout does not emit false`() = runTest {
        val delegate = TestNetworkMonitor(initialOnline = true)
        val monitor = DebouncedNetworkMonitor(delegate = delegate, appScope = backgroundScope)

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(emissions::add)
        }
        testScheduler.runCurrent()
        assertEquals(listOf(true), emissions)

        delegate.setConnected(false)
        testScheduler.advanceTimeBy(299)
        testScheduler.runCurrent()
        assertEquals(listOf(true), emissions)

        delegate.setConnected(true)
        testScheduler.advanceTimeBy(500)
        testScheduler.runCurrent()
        assertEquals(listOf(true), emissions)
        job.cancel()
    }

    @Test
    fun `false to true recovery emits immediately with 0ms delay`() = runTest {
        val delegate = TestNetworkMonitor(initialOnline = false)
        val monitor = DebouncedNetworkMonitor(delegate = delegate, appScope = backgroundScope)

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(emissions::add)
        }
        testScheduler.runCurrent()
        assertEquals(listOf(false), emissions)

        delegate.setConnected(true)
        testScheduler.runCurrent()
        assertEquals(listOf(false, true), emissions)
        job.cancel()
    }

    @Test
    fun `multiple collectors share a single upstream subscription`() = runTest {
        var activeUpstreamSubscriptions = 0
        var totalUpstreamSubscriptions = 0
        val upstream = MutableStateFlow(true)
        val delegate = object : NetworkMonitor {
            override val isOnline: Flow<Boolean> = upstream
                .onStart {
                    activeUpstreamSubscriptions++
                    totalUpstreamSubscriptions++
                }
                .onCompletion {
                    activeUpstreamSubscriptions--
                }
        }
        val monitor = DebouncedNetworkMonitor(delegate = delegate, appScope = backgroundScope)

        val emissions1 = mutableListOf<Boolean>()
        val emissions2 = mutableListOf<Boolean>()
        val job1 = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(emissions1::add)
        }
        val job2 = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(emissions2::add)
        }
        testScheduler.runCurrent()

        assertEquals(1, activeUpstreamSubscriptions)
        assertEquals(1, totalUpstreamSubscriptions)
        assertEquals(listOf(true), emissions1)
        assertEquals(listOf(true), emissions2)

        upstream.value = false
        testScheduler.advanceTimeBy(300)
        testScheduler.runCurrent()

        assertEquals(listOf(true, false), emissions1)
        assertEquals(listOf(true, false), emissions2)

        job1.cancel()
        testScheduler.runCurrent()
        assertEquals(1, activeUpstreamSubscriptions)

        job2.cancel()
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(0, activeUpstreamSubscriptions)
    }

    @Test
    fun `resubscribing after stop timeout clears stale replay cache`() = runTest {
        val delegate = TestNetworkMonitor(initialOnline = true)
        val monitor = DebouncedNetworkMonitor(delegate = delegate, appScope = backgroundScope)

        val firstEmissions = mutableListOf<Boolean>()
        val job1 = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(firstEmissions::add)
        }
        testScheduler.runCurrent()

        delegate.setConnected(false)
        testScheduler.advanceTimeBy(300)
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), firstEmissions)

        job1.cancel()
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()

        // Network recovers while no subscribers are active
        delegate.setConnected(true)

        val secondEmissions = mutableListOf<Boolean>()
        val job2 = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.isOnline.collect(secondEmissions::add)
        }
        testScheduler.runCurrent()

        // Must receive fresh `true` directly without replaying stale `false`
        assertEquals(listOf(true), secondEmissions)
        job2.cancel()
    }
}
