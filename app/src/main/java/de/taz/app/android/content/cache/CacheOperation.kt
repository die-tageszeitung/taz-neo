package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.download.*
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.Exception
import kotlinx.coroutines.launch


/**
 * Class representing a chunk of items that are part of some cached content that
 * are processed in some error prone, asynchronous operation. (Downloading, storing, erasing).
 * It implements function informing about progress and outcome of individual and overall (sub)-operations
 * @param applicationContext An android application context object
 * @param items The items that are to be downloaded or deleted in this operation.
 * @param targetState The state of the cache after this operation succeeded
 * @param tag The tag used for registering this operation
 * @param priority The priority used while downloading files belonging to this operation
 */
abstract class CacheOperation<ITEM : CacheItem, RESULT>(
    val applicationContext: Context,
    private val items: List<ITEM>,
    private val targetState: CacheState,
    val tag: String,
    var priority: DownloadPriority = DownloadPriority.Normal
) {
    /**
     * The companion object implements shared state as the map of active operations
     * that needs to be synchronous to keep track of any cache operation ongoing and mutually
     * exclude potentially conflicting, parallel operations on the same item
     */
    companion object {
        private val log by Log
        internal val activeCacheOperations = ConcurrentHashMap<String, AnyCacheOperation>()
        internal val cacheStatusFlow = MutableSharedFlow<Pair<String, CacheStateUpdate>>()
            .also { flow ->
                CoroutineScope(Dispatchers.Default).launch {
                    flow
                        .map { it.second }
                        .collect {
                            val operationName = it.operation?.let { it::class.simpleName }
                            log.verbose("${it.operation?.tag} ${operationName}: ${it.type} - ${it.cacheState}")
                        }
                }
            }
    }

    val log by Log
    protected val issueRepository = IssueRepository.getInstance(applicationContext)

    /**
     * The loading state is different for each discrete implementation of [CacheOperation]
     */
    protected abstract val loadingState: CacheState

    /**
     * The result of this operation. Should never be null after [notifySuccess]
     */
    private var result: RESULT? = null

    /**
     * The CacheItems that should be processed in this operation. It gets initialized with the
     * items in the constructor argument
     */
    private val _cacheItems = items.map { item ->
        // The CacheOperation overrides the priority of a CacheItem!
        // That allows us to dynamically rewrite the order as the user navigates
        item.priority = { this@CacheOperation.priority }
        CacheOperationItem(item, this)
    }.toMutableList()

    /**
     * The CacheItems that should be processed in this operation.
     */
    val cacheItems: List<CacheOperationItem<ITEM>>
        get() = _cacheItems

    /**
     * Are all items complete? (Attention, for operations without items that is always true)
     */
    private val totalItemCount
        get() = cacheItems.count()

    protected var successfulCount: Int = 0
    protected var failedCount: Int = 0

    /**
     * Items that are either successful or failed
     */
    private val completedItemCount
        get() = successfulCount + failedCount

    /**
     * Are all items complete? (Attention, for operations without items that is always true)
     */
    private val itemsComplete
        get() = completedItemCount == totalItemCount

    /**
     * StateFlow of the state of the CacheOperation
     * This is can be used to wait until the [CacheStateUpdate.Type] has a certain value by using
     * [Flow.first] - e.g. `stateFlow.first { it.hasCompleted }`
     */
    private val stateFlow = MutableStateFlow(
        CacheStateUpdate(
            CacheStateUpdate.Type.INITIAL,
            CacheState.ABSENT,
            completedItemCount,
            totalItemCount,
            this
        )
    )

    /**
     * Latest state of this operation.
     */
    val state
        get() = stateFlow.value

    /**
     * Execute this operation
     * ! Attention it is not guaranteed to be this exact operation
     * If a different Operation on the same tag is being executed the
     * running operation will be awaited before this operation is executed.
     * @param forceExecution   If this flag is set to true the operation will be executed even if a
     *                          another operation of the same type is currently running.
     *                          Instead of waiting on the result of the running this operation is enqueued and
     *                          executed afterwards
     * The function will suspend until the [CacheOperation] is completed
     *
     * TODO: we currently do not consider the maxRetries when waiting -
     *       this means that a CacheOperation with infinite retries might wait for one with 3 and
     *       fail if the 3 attempts do not succeed
     */
    suspend fun execute(forceExecution: Boolean = false): RESULT = withContext(NonCancellable) {
        try {
            registerOperation()
        } catch (e: SameOperationActiveException) {
            log.warn("Operation with tag $tag and class ${this::class.simpleName} is already scheduled")

            if (forceExecution) {
                // Wait for the blocking operation to finish but disregard any outcome
                try { e.blockingOperation.waitOnCompletion() } catch (e: Exception) {}
                // Disregard the fact that there is a duplicating operation and execute this one
                return@withContext execute(forceExecution)
            } else {
                // wait on the blocking operation instead

                // if the blocking operation is of less priority than the new one bump it up
                if (priority > e.blockingOperation.priority) {
                    e.blockingOperation.priority = priority
                }
                // Exception SameOperationActiveException guarantees us to have the same RESULT
                return@withContext e.blockingOperation.waitOnCompletion() as RESULT
            }
        } catch (e: DifferentOperationActiveException) {
            // Wait for the blocking operation to finish but disregard any outcome
            try { e.blockingOperation.waitOnCompletion() } catch (e: Exception) {}
            return@withContext execute(forceExecution)
        }
        return@withContext doWork()
    }

    /**
     * In some operations (i.e. in [WrappedDownload]) it could happen that during the process
     * more items will need to be added to the opeeation than known at the start of the operation.
     * For instance the files needed to be downloaded for an [de.taz.app.android.api.models.Issue]
     * is only known after a [MetadataDownload] for that [de.taz.app.android.api.models.Issue] is
     * done.
     *
     * @param items The [CacheOperationItem]s to be added to this operation
     */
    fun addItems(items: List<ITEM>) {
        if (state.hasCompleted) {
            throw IllegalStateException("Cannot add new items if the operation is already marked as complete")
        }
        _cacheItems.addAll(items.map {
            CacheOperationItem(it, this).apply {
                // The CacheOperation overrides the priority of a CacheItem!
                // That allows us to dynamically rewrite the order as the user navigates
                item.priority = { this@CacheOperation.priority }
            }
        })
    }

    /**
     * In some operations (i.e. in [WrappedDownload]) it could happen that during the process
     * more items will need to be added to the operation than known at the start of the operation.
     * For instance the files needed to be downloaded for an [de.taz.app.android.api.models.Issue]
     * is only known after a [MetadataDownload] for that [de.taz.app.android.api.models.Issue] is
     * done.
     *
     * @param item The [CacheOperationItem] to be added to this operation
     */
    fun addItem(item: ITEM) = addItems(listOf(item))

    /**
     * This function needs to implemented by the discrete implementations
     * Here the volatile operations can be made. In the course of the
     * operation at least [notifyStart] and [notifySuccess] or [notifyFailure] needs
     * to be called to indicate progress.
     * The implementation should suspend until the [CacheOperation] is completed, so that
     * any caller to [execute] can be sure the [CacheOperation] is done after invoking the function.
     */
    protected abstract suspend fun doWork(): RESULT

    /**
     * Function to register a CacheOperation in the central operation hashmap, forward updates
     * to the central cacheStatusFlow and removes it from the activeCacheOperations once finished
     */
    private suspend fun registerOperation() {
        val operation = activeCacheOperations.getOrPut(tag) { this }
        if (operation != this) {
            if (this::class == operation::class) {
                throw SameOperationActiveException(
                    "For tag $tag there is the same operation already active",
                    operation
                )
            } else {
                throw DifferentOperationActiveException(
                    "For tag $tag there is a different operation already active",
                    operation
                )
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            stateFlow.collect { update ->
                cacheStatusFlow.emit(tag to update)

                if (update.hasCompleted) {
                    // stop collecting
                    cancel()
                }
            }
        }
    }

    /**
     * Emits an update indicating recoverable connection troubles while executing
     * this operation
     */
    fun notifyBadConnection() {
        emitUpdate(
            CacheStateUpdate(
                CacheStateUpdate.Type.BAD_CONNECTION,
                this.state.cacheState,
                completedItemCount,
                totalItemCount,
                this
            )
        )
    }


    /**
     * Counts a successful item and emits an update indicating that
     */
    fun notifySuccessfulItem() {
        successfulCount++
        log.verbose(
            "Notifying a successful file in $tag.\n" +
                    "Now $successfulCount/$failedCount/$completedItemCount succeeded of $totalItemCount"
        )
        emitUpdate(
            CacheStateUpdate(
                CacheStateUpdate.Type.ITEM_SUCCESSFUL,
                this.state.cacheState,
                completedItemCount,
                totalItemCount,
                this
            )
        )
    }

    /**
     * Counts a failed item and emits an update indicating that
     * @param exception The exception that is the cause of this failed item
     */
    fun notifyFailedItem(exception: Exception) {
        failedCount++
        log.verbose(
            "Notifying a failed file in $tag. with reason $exception \n" +
                    "Now $successfulCount/$failedCount/$completedItemCount succeeded of $totalItemCount"
        )

        emitUpdate(
            CacheStateUpdate(
                CacheStateUpdate.Type.ITEM_FAILED,
                this.state.cacheState,
                completedItemCount,
                totalItemCount,
                this,
                exception
            )
        )
    }

    /**
     *
     */
    fun checkIfItemsCompleteAndNotifyResult(result: RESULT) {
        if (state.hasCompleted) {
            return
        }
        if (!state.hasCompleted && itemsComplete) {
            if (failedCount == 0) {
                notifySuccess(result)
            } else {
                notifyFailure(
                    CacheOperationFailedException("Some items were not success fully processed")
                )
            }
        }
    }

    fun notifyStart() {
        emitUpdate(
            CacheStateUpdate(
                this.state.type,
                loadingState,
                completedItemCount,
                totalItemCount,
                this
            )
        )
    }

    /**
     * Mark the operation as failed, will emit failiure updates to its listeners
     * @param e An exception indicating the cause of the failiure
     */
    fun notifyFailure(e: Exception) {
        // remove from activeCacheOperations
        activeCacheOperations.remove(tag)

        emitUpdate(
            CacheStateUpdate(
                CacheStateUpdate.Type.FAILED,
                CacheState.ABSENT,
                completedItemCount,
                totalItemCount,
                this,
                e
            )
        )
    }

    /**
     * Mark the operation as succeeded, will emit success updates to its listeners
     * @param result The result of the operation, if the operation doesn't produce a result use [Unit]
     */
    fun notifySuccess(result: RESULT) {
        this.result = result

        // remove from activeCacheOperations
        activeCacheOperations.remove(tag)

        emitUpdate(
            CacheStateUpdate(
                CacheStateUpdate.Type.SUCCEEDED,
                targetState,
                completedItemCount,
                totalItemCount,
                this
            )
        )
    }

    /**
     * Suspend until the operation is either [CacheStateUpdate.Type.FAILED] or
     * [CacheStateUpdate.Type.SUCCEEDED]
     */
    protected suspend fun waitOnCompletion(): RESULT = withContext(Dispatchers.Default) {
        if (state.hasCompleted) return@withContext result!!

        // wait until failed or completed
        stateFlow.first { it.hasCompleted }

        // rethrow the exception if failed
        if (state.hasFailed)
            throw state.exception ?: Exception("Unknown CacheOperation error")
        // otherwise return result
        requireNotNull(getResult()) {
            "CacheOperation is completed, there is no exception so should have a result"
        }
    }

    private fun getResult(): RESULT {
        if (!state.hasCompleted) {
            throw IllegalStateException("Cannot get operation result if operation not complete")
        } else {
            return result ?: throw IllegalStateException("Result is null despite complete state")
        }
    }

    /**
     * Set the latest update and emit it to listeners and livedata
     * @param update the update to emit
     */
    private fun emitUpdate(update: CacheStateUpdate) {
        if (!this.state.hasCompleted) {
            // It is illegal to modify the state of a completed operation
            this.stateFlow.value = update
        } else {
            log.warn("tried to update completed CacheOperation $tag with update: $update")
        }
    }
}