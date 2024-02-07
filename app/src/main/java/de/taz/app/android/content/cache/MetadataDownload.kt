package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.METADATA_DOWNLOAD_RETRY_INDEFINITELY
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.api.models.AppInfoKey
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoKey
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.persistence.repository.FrontPageKey
import de.taz.app.android.persistence.repository.FrontpagePublication
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.persistence.repository.MomentKey
import de.taz.app.android.persistence.repository.MomentPublication
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.simpleDateFormat

/**
 * An operation downloading the Metadata of a given object
 *
 * @param applicationContext An android application context object
 * @param tag The tag on which this operation should be registered
 * @param download The object of which the metadata should be deleted
 * @param allowCache If the cache of Metadata should be used if existend (download will be skipped then)
 * @param retriesOnConnectionError Specify the amount of retries that should be attempted after (recoverable) connection errors
 * @param minStatus The minimum status expected if [download] is of type [IssuePublication]
 */
class MetadataDownload(
    applicationContext: Context,
    tag: String,
    val download: ObservableDownload,
    private val allowCache: Boolean,
    private val retriesOnConnectionError: Int,
    private val minStatus: IssueStatus
) : CacheOperation<MetadataCacheItem, ObservableDownload>(
    applicationContext, emptyList(), CacheState.METADATA_PRESENT, tag
) {
    override val loadingState: CacheState = CacheState.LOADING_METADATA
    private val apiService = ApiService.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

    companion object {
        /**
         * Creates a [MetadataDownload] of a given object
         *
         * @param context An android context object
         * @param tag The tag on which this operation should be registered
         * @param download The object of which the metadata should be deleted
         * @param allowCache If the cache of Metadata should be used if existend (download will be skipped then)
         */
        fun prepare(
            context: Context,
            download: ObservableDownload,
            tag: String,
            allowCache: Boolean = false,
            retriesOnConnectionError: Int = METADATA_DOWNLOAD_RETRY_INDEFINITELY,
            minStatus: IssueStatus = IssueStatus.public
        ): MetadataDownload {
            return MetadataDownload(
                context,
                tag,
                download,
                allowCache,
                retriesOnConnectionError,
                minStatus
            )
        }
    }

    override suspend fun doWork(): ObservableDownload {
        notifyStart()
        // If we were supplied with an issue key or an issue we want to fetch the newest version of it before
        // downloading, as stale data is bad. In any other collection we cant and will use whatever is in DB
        return try {
            when (download) {
                is IssuePublication -> getIssue(download)
                is IssuePublicationWithPages -> IssueWithPages(getIssue(download))
                is IssueKey -> getIssue(download)
                is MomentKey -> getMoment(download)
                is MomentPublication -> getMoment(download)
                is FrontpagePublication -> getFrontPage(download)
                is FrontPageKey -> getFrontPage(download)
                is IssueKeyWithPages -> IssueWithPages(getIssue(IssueKey(download)))
                is IssueOperations -> getIssue(download.issueKey)
                is AppInfoKey -> getAppInfo()
                is ResourceInfoKey -> getResourceInfo(download)
                // Other collections like Articles, Sections, Pages are not directly queryable,
                // so updates only are possible by requerying the whole issue
                is DownloadableCollection -> download
                else -> {
                    val exception =
                        IllegalArgumentException("MetadataDownload is not valid with ${download::class.simpleName}")
                    throw CacheOperationFailedException(
                        "MetadataDownload failed because of bad arguments",
                        exception
                    )
                }
            }.also {
                notifySuccess(it)
            }
        } catch (e: ConnectivityException.Recoverable) {
            notifyFailure(e)
            throw CacheOperationFailedException(
                "Could not retrieve Metadata because of a connectivity issue",
                e
            )
        } catch (e: Exception) {
            notifyFailure(e)
            throw e
        }
    }

    private suspend fun getAppInfo(): AppInfo {
        if (allowCache) {
            val cachedAppInfo = appInfoRepository.get()
            if (cachedAppInfo != null) {
                return cachedAppInfo
            }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val appInfo = apiService.getAppInfo()
            appInfoRepository.save(appInfo)
        }
    }

    private suspend fun getResourceInfo(key: ResourceInfoKey): ResourceInfo {
        if (allowCache) {
            val cachedResourceInfo = resourceInfoRepository.getNewest()
            if (cachedResourceInfo != null && cachedResourceInfo.resourceVersion >= key.minVersion) {
                return cachedResourceInfo
            }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val resourceInfo = apiService.getResourceInfo()
            if (resourceInfo.resourceVersion >= key.minVersion) {
                resourceInfoRepository.save(resourceInfo)
            } else {
                throw CacheOperationFailedException(
                    "ResourceInfo on server is lesser than requested ${key.minVersion}"
                )
            }
        }
    }

    private suspend fun getMoment(momentPublication: MomentPublication): Moment {
        if (allowCache) {
            var cachedMoment: Moment? = null
            for (status in IssueStatus.values().sortedArrayDescending()) {
                cachedMoment = momentRepository.get(
                    MomentKey(momentPublication.feedName, momentPublication.date, status)
                )
                if (cachedMoment != null) {
                    // cache hit
                    break
                }
            }
            if (cachedMoment != null && cachedMoment.issueStatus >= minStatus) {
                // At this point we could have gotten any issueStatus, so
                // only return if the minStatus is met
                return cachedMoment
            }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val moment = apiService.getMomentByFeedAndDate(
                momentPublication.feedName,
                simpleDateFormat.parse(momentPublication.date)!!
            )
            if (moment != null && moment.issueStatus >= minStatus) {
                momentRepository.save(moment)
            } else {
                throw CacheOperationFailedException(
                    "Unable to retrieve issue by publication. Min status $minStatus not being met"
                )
            }
        }
    }

    private suspend fun getMoment(momentKey: MomentKey): Moment {
        if (allowCache) {
            val cachedMoment = momentRepository.get(
                momentKey
            )
            if (cachedMoment != null) {
                return cachedMoment
            }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val moment = apiService.getMomentByFeedAndDate(
                momentKey.feedName,
                simpleDateFormat.parse(momentKey.date)!!
            )

            if (moment == null && momentKey != moment?.momentKey) {
                throw CacheOperationFailedException(
                    "The API returned an unexpected IssueKey (${moment?.issueKey})" +
                            "Expected $momentKey. Wrong credentials?"
                )
            }
            momentRepository.save(moment)
        }
    }

    private suspend fun getFrontPage(frontpagePublication: FrontpagePublication): Page {
        if (allowCache) {
            val (cachedPage, cachedStatus) = pageRepository.getFrontPage(
                IssueKey(
                    frontpagePublication.feedName,
                    frontpagePublication.date,
                    IssueStatus.regular
                )
            )?.let { it to IssueStatus.regular } ?: pageRepository.getFrontPage(
                IssueKey(
                    frontpagePublication.feedName,
                    frontpagePublication.date,
                    IssueStatus.public
                )
            ) to IssueStatus.public
            if (cachedPage != null && cachedStatus >= minStatus) {
                // At this point we could have gotten any issueStatus, so
                // only return if the minStatus is met
                return cachedPage
            }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val (frontPage, frontPageStatus) = apiService.getFrontPageByFeedAndDate(
                frontpagePublication.feedName,
                simpleDateFormat.parse(frontpagePublication.date)!!
            ) ?: throw CacheOperationFailedException("No frontpage found for $frontpagePublication")
            if (frontPageStatus >= minStatus) {
                return@retryOnConnectionFailure pageRepository.saveFrontPage(
                    frontPage, IssueKey(
                        frontpagePublication.feedName,
                        frontpagePublication.date,
                        frontPageStatus
                    )
                )
            } else {
                throw CacheOperationFailedException(
                    "Unable to retrieve issue by publication. Min status $minStatus not being met"
                )
            }
        }
    }

    private suspend fun getFrontPage(frontPageKey: FrontPageKey): Page {
        if (allowCache) {
            pageRepository.getFrontPage(
                IssueKey(
                    frontPageKey.feedName,
                    frontPageKey.date,
                    frontPageKey.status
                )
            )?.let { return it }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val (frontPage, frontPageStatus) = apiService.getFrontPageByFeedAndDate(
                frontPageKey.feedName,
                simpleDateFormat.parse(frontPageKey.date)!!
            ) ?: throw CacheOperationFailedException("No frontpage found for $frontPageKey")
            if (frontPageStatus >= minStatus) {
                return@retryOnConnectionFailure pageRepository.saveFrontPage(
                    frontPage, IssueKey(
                        frontPageKey.feedName,
                        frontPageKey.date,
                        frontPageKey.status
                    )
                )
            } else {
                throw CacheOperationFailedException(
                    "Unable to retrieve issue by publication. Min status $minStatus not being met"
                )
            }
        }
    }

    private suspend fun getIssue(issuePublication: AbstractIssuePublication): Issue {
        if (allowCache) {
            var cachedIssue: Issue? = null
            for (status in IssueStatus.values().sortedArrayDescending()) {
                cachedIssue = issueRepository.get(
                    IssueKey(issuePublication.feedName, issuePublication.date, status)
                )
                if (cachedIssue != null) {
                    // cache hit
                    break
                }
            }
            if (cachedIssue != null && cachedIssue.status >= minStatus) {
                // At this point we could have gotten any issueStatus, so
                // only return if the minStatus is met
                return cachedIssue
            }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val issue = apiService.getIssueByPublication(issuePublication)
            if (issue.status >= minStatus) {
                issueRepository.save(issue)
            } else {
                throw CacheOperationFailedException(
                    "Unable to retrieve issue by publication. Min status $minStatus not being met"
                )
            }
        }
    }

    private suspend fun getIssue(issueKey: AbstractIssueKey): Issue {
        if (allowCache) {
            val cachedIssue = issueRepository.get(
                IssueKey(issueKey)
            )
            if (cachedIssue != null) {
                return cachedIssue
            }
        }
        return apiService.retryOnConnectionFailure(maxRetries = retriesOnConnectionError) {
            val issue = apiService.getIssueByPublication(IssuePublication(issueKey))
            if (issueKey != issue.issueKey) {
                throw CacheOperationFailedException(
                    "The API returned an unexpected IssueKey (${issue.issueKey})" +
                            "Expected $issueKey. Wrong credentials?"
                )
            }
            issueRepository.save(issue)
        }
    }
}