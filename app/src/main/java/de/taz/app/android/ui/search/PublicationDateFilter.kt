package de.taz.app.android.ui.search

import java.util.Date

sealed class PublicationDateFilter {
    object Any : PublicationDateFilter()
    object LastDay : PublicationDateFilter()
    object Last7Days : PublicationDateFilter()
    object Last31Days : PublicationDateFilter()
    object Last365Days : PublicationDateFilter()
    data class Custom(val from: Date?, val until: Date?): PublicationDateFilter()
}