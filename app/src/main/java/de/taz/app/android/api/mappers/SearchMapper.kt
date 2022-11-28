package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SearchDto
import de.taz.app.android.api.models.Search

object SearchMapper {
    fun from(searchDto: SearchDto): Search {
        return Search(
            AuthInfoMapper.from(searchDto.authInfo),
            searchDto.sessionId,
            searchDto.searchHitList?.map { SearchHitMapper.from(it) },
            searchDto.total,
            searchDto.totalFound,
            searchDto.time,
            searchDto.offset,
            searchDto.rowCnt,
            searchDto.next,
            searchDto.prev,
            searchDto.text,
            searchDto.author,
            searchDto.title,
            searchDto.snippetWords,
            searchDto.sorting,
            searchDto.searchTime,
            searchDto.pubDateFrom,
            searchDto.pubDateUntil,
            searchDto.minPubDate
        )
    }
}

