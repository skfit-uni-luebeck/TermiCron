package de.uzl.itcr.termicron.synchronization

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.configuration.MdrConfiguration

abstract class MdrSynchronization {

    fun synchro(vs: ValueSetExpansion): SynchronizationOutcome {
        val isPresent = isPresent(vs) //store once to prevent multiple requests
        return when {
            isPresent && isCurrent(vs) -> SynchronizationOutcome.NO_NEED
            isPresent -> if (update(vs)) SynchronizationOutcome.UPDATED else SynchronizationOutcome.ERROR //implies !isCurrent(vs)
            create(vs) -> SynchronizationOutcome.CREATED
            else -> SynchronizationOutcome.ERROR
        }
    }

    abstract fun isPresent(vs: ValueSetExpansion): Boolean
    abstract fun isCurrent(vs: ValueSetExpansion): Boolean
    abstract fun create(vs: ValueSetExpansion): Boolean
    abstract fun update(vs: ValueSetExpansion): Boolean
}