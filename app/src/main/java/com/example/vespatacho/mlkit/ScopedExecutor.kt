package com.example.vespatacho.mlkit

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps an existing executor to provide a [.shutdown] method that allows subsequent
 * cancellation of submitted runnable.
 */
class ScopedExecutor(private val executor: Executor) : Executor {
    private val shutdown = AtomicBoolean()

    override fun execute(command: Runnable) {
        // Return early if this object has been shut down.
        if (shutdown.get()) {
            return
        }
        executor.execute(
            Runnable {
                // Check again in case it has been shut down in the meantime.
                if (shutdown.get()) {
                    return@Runnable
                }
                command.run()
            })
    }

    /**
     * After this method is called, no runnable that have been submitted or are subsequently
     * submitted will start to execute, turning this executor into a no-op.
     * 
     * 
     * Runnable that have already started to execute will continue.
     */
    fun shutdown() {
        shutdown.set(true)
    }
}
