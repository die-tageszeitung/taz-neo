package de.taz.app.android.content.cache

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.data.DataService
import de.taz.app.android.download.*
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.Exception
import kotlin.collections.HashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Class representing a chunk of items that are part of some cached content that
 * are processed in some error prone, asynchronous operation. (Downloading, storing, erasing).
 * It implements function informing about progress and outcome of individual and overall (sub)-operations
 * @param context An android context object
 * @param items The items that are to be downloaded or deleted in this operation.
 * @param targetState The state of the cache after this operation succeeded
 * @param tag The tag used for registering this operation
 * @param priority The priority used while downloading files belonging to this operation
 */
abstract class CacheOperation<ITEM : CacheItem, RESULT>(
    val context: Context,
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
        private val cacheOperationMutex = Mutex()
        internal val activeCacheOperations = HashMap<String, AnyCacheOperation>()
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
    protected val dataService = DataService.getInstance(context)
    protected val issueRepository = IssueRepository.getInstance(context)

    /**
     * The loading state is different for each discrete implementaion of [CacheOperation]
     */
    protected abstract val loadingState: CacheState

    /**
     * Currently registered listeners
     */
    private val listeners = LinkedList<CacheStateListener<RESULT>>()

    /**
     * The result of this operation. Should never be null after [notifySuccess]
     */
    private var result: RESULT? = null

    /**
     * The CacheItems that should be processed in this operation. It gets initialized with the
     * items in the constructor argument
     */
    private val _cacheItems = mutableListOf<CacheOperationItem<ITEM>>().also { addItems(items) }

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
     * Latest state of this operation.
     */
    private var _state: CacheStateUpdate = CacheStateUpdate(
        CacheStateUpdate.Type.INITIAL,
        CacheState.ABSENT,
        completedItemCount,
        totalItemCount,
        this
    )
    val state: CacheStateUpdate
        get() = _state


    private val _stateLiveData = MutableLiveData<CacheStateUpdate>()
    val stateLiveData: LiveData<CacheStateUpdate> = _stateLiveData


    /**
     * A Mutex to avoid concurrent (notifications) modifications of the operation state
     */
    private val notifyLock = Mutex()

    /**
     * Execute this operation
     * ! Attention it is not guaranteed to be this exact operation
     * If an operation of the same type and the same tag is already active, instead of
     * executing this operation it will listen on the active one.
     * If a different Operation on the same tag is being executed the
     * running operation will be awaited before this operation is executed.
     * The function will suspend until the [CacheOperation] is completed
     */
    suspend fun execute(): RESULT {
        try {
            registerOperation()
        } catch (e: SameOperationActiveException) {
            log.warn("Operation with tag $tag and class ${this::class.simpleName} is already scheduled")
            // if the blocking operation is of less priority than the new one bump it up
            if (priority > e.blockingOperation.priority) {
                e.blockingOperation.priority = priority
            }

            // wait on the blocking operation instead
            e.blockingOperation.waitOnCompletion()
            // Exception SameOperationActiveException guarantees us to have the same RESULT
            return e.blockingOperation.getResult() as RESULT
        } catch (e: DifferentOperationActiveException) {
            // wait for the previous operation and then retry registering and execution
            e.blockingOperation.waitOnCompletion()
            return execute()
        }
        return doWork()
    }

    /**
     * In some operations (i.e. in [WrappedDownload]) it could happen that during the process
     * more items will need to be added to the opreation than known at the start of the operation.
     * For instance the files needed to be downloaded for an [de.taz.app.android.api.models.Issue]
     * is only known after a [MetadataDownload] for that [de.taz.app.android.api.models.Issue] is
     * done.
     *
     * @param items The [CacheOperationItem] to be added to this operation
     */
    fun addItems(items: List<ITEM>) {
        if (state.complete) {
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
     * This function needs to implemented by the discrete implementations
     * Here the volatile operations can be made. In the course of the
     * operation at least [notifyStart] and [notifySuccess] or [notifyFailiure] needs
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
        cacheOperationMutex.withLock {
            val operation = activeCacheOperations[tag]
            if (operation != null) {
                if (this::class == operation::class) {
                    throw SameOperationActiveException(
                        "For tag $tag there is the same operation already active",
                        operation
                    )
                } else {
                    throw DifferentOperationActiveException(
                        "For tag $tag there is a diffrent operation already active",
                        operation
                    )
                }
            } else {
                activeCacheOperations[tag] = this
            }
        }

        addListener(object : CacheStateListener<RESULT> {
            override fun onUpdate(update: CacheStateUpdate) {
                if (update.complete) {
                    activeCacheOperations.remove(tag)
                }
                // We need to emit this blockingly to ensure sequentially correct updates
                runBlocking {
                    cacheStatusFlow.emit(tag to update)
                }
            }
        })
    }

    /**
     * Adds a Listener to this operation
     * @param listener The [CacheStateUpdate] to add to this operation
     * @return The just added listener
     */
    suspend fun addListener(listener: CacheStateListener<RESULT>): CacheStateListener<RESULT> =
        notifyLock.withLock {
            if (state.complete) {
                if (state.type == CacheStateUpdate.Type.FAILED) {
                    listener.onFailuire(
                        state.exception ?: Exception("Unknown CacheOperation error")
                    )
                } else if (state.type == CacheStateUpdate.Type.SUCCEEDED) {
                    listener.onSuccess(getResult())
                }
            } else {
                listeners.add(listener)
            }
            return listener
        }

    /**
     * Remove a Listener from this operation, it will no longer recieve updates
     * @param listener The [CacheStateUpdate] to remove from this operation
     */
    fun removeListener(listener: CacheStateListener<RESULT>) {
        listeners.remove(listener)
    }

    /**
     * Emits an update indicating recoverable connection troubles while executing
     * this operation
     */
    suspend fun notifyBadConnection() = notifyLock.withLock {
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
    suspend fun notifySuccessfulItem() = notifyLock.withLock {
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
    suspend fun notifyFailedItem(exception: Exception) = notifyLock.withLock {
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
    suspend fun checkIfItemsCompleteAndNotifyResult(result: RESULT) = notifyLock.withLock {
        if (state.complete) {
            return
        }
        if (!state.complete && itemsComplete) {
            if (failedCount == 0) {
                emitSuccess(result)
            } else {
                emitFailiure(
                    CacheOperationFailedException(
                        "Some items were not success fully processed"
                    )
                )
            }
        }
    }

    suspend fun notifyStart() = notifyLock.withLock {
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
    suspend fun notifyFailiure(e: Exception) = notifyLock.withLock {
        emitFailiure(e)
    }

    /**
     * Mark the operation as succeeded, will emit success updates to its listeners
     * @param result The result of the operation, if the operation doesn't produce a result use [Unit]
     */
    suspend fun notifySuccess(result: RESULT) = notifyLock.withLock {
        emitSuccess(result)
    }

    /**
     * Suspend until the operation is either [CacheStateUpdate.Type.FAILED] or
     * [CacheStateUpdate.Type.SUCCEEDED]
     */
    suspend fun waitOnCompletion() = withContext(Dispatchers.Default) {
        if (state.complete) return@withContext
        suspendCoroutine<RESULT> { continuation ->
            launch {
                addListener(object : CacheStateListener<RESULT> {
                    override fun onFailuire(e: Exception) {
                        continuation.resumeWithException(e)
                    }

                    override fun onSuccess(result: RESULT) {
                        continuation.resume(result)
                    }
                })
            }
        }
    }

    private fun getResult(): RESULT {
        if (!state.complete) {
            throw IllegalStateException("Cannot get operation result if operation not complete")
        } else {
            return result ?: throw IllegalStateException("Result is null despite complete state")
        }
    }

    /**
     * Emits failiure update and failiure to listeners
     * @param e An exception indicating the cause of the failiure
     */
    private suspend fun emitFailiure(e: Exception) {
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
        listeners.map { it.onFailuire(e) }
    }

    /**
     * Emits success update and success to listeners
     * @param result To emit success a result must be provided. If the operation doesn't produce anything use [Unit]
     */
    private fun emitSuccess(result: RESULT) {
        this.result = result
        emitUpdate(
            CacheStateUpdate(
                CacheStateUpdate.Type.SUCCEEDED,
                targetState,
                completedItemCount,
                totalItemCount,
                this
            )
        )
        listeners.map { it.onSuccess(result) }
    }

    /**
     * Set the latest update and emit it to listeners and livedata
     * @param update the update to emit
     */
    private fun emitUpdate(update: CacheStateUpdate) {
        if (this.state.complete) {
            throw IllegalStateException("It is illegal to modify the state of a completed operation")
        }
        this._state = update
        _stateLiveData.postValue(update)
        listeners.map { it.onUpdate(update) }
    }
}