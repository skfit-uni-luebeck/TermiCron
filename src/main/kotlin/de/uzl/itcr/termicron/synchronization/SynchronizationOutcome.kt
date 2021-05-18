package de.uzl.itcr.termicron.synchronization

/**
 * enum class to represent the status of an MDR synchronization operation
 */
enum class SynchronizationOutcome {
    /**
     * the vs was not present before, and is now created
     */
    CREATED,

    /**
     * the vs was present before, and is now updated
     */
    UPDATED,

    /**
     * the vs is present and current, nothing was done
     */
    NO_NEED,

    /**
     * the synchronization was not successful, check the logs.
     */
    ERROR
}