package de.taz.test

import de.taz.app.android.api.ApiService
import de.taz.app.android.api.GraphQlClient
import de.taz.app.android.api.QueryService
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.FeedService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.dataStore.AudioPlayerDataStore
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.download.FileDownloader
import de.taz.app.android.firebase.FirebaseDataStore
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.AudioRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.persistence.repository.ViewerStateRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.IssueCountHelper
import de.taz.app.android.singletons.NightModeHelper
import de.taz.app.android.singletons.NotificationHelper
import de.taz.app.android.singletons.StoragePathService
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.SubscriptionPollHelper
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.SingletonHolder

object SingletonTestUtil {

    private val allSingletons = listOf<SingletonHolder<*, *>>(
        ApiService,
        GraphQlClient,
        QueryService,
        AudioPlayerService,
        ContentService,
        FeedService,
        DownloadScheduler,
        AudioPlayerDataStore,
        CoachMarkDataStore,
        DownloadDataStore,
        GeneralDataStore,
        StorageDataStore,
        TazApiCssDataStore,
        FileDownloader,
        FirebaseDataStore,
        FirebaseHelper,
        AppDatabase,
        AppInfoRepository,
        ArticleRepository,
        AudioRepository,
        BookmarkRepository,
        FeedRepository,
        FileEntryRepository,
        ImageRepository,
        IssueRepository,
        MomentRepository,
        PageRepository,
        ResourceInfoRepository,
        SectionRepository,
        ViewerStateRepository,
        AuthHelper,
        FontHelper,
        IssueCountHelper,
        NightModeHelper,
        NotificationHelper,
        StoragePathService,
        StorageService,
        SubscriptionPollHelper,
        TazApiCssHelper,
        ToastHelper,
        Tracker,
    )

    fun resetAll() {
        allSingletons.forEach { it.inject(null) }
    }
}