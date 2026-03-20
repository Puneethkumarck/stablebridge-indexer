package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.BloomStatusResponse;
import com.stablebridge.indexer.api.IndexerStatusResponse;
import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainStatus;
import com.stablebridge.indexer.domain.model.NetworkType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
interface StatusControllerMapper {

    @Mapping(target = "chainId", source = "chainName")
    @Mapping(target = "networkType", expression = "java(chainStatus.networkType().name())")
    @Mapping(target = "workerState", expression = "java(chainStatus.workerState().name())")
    IndexerStatusResponse toResponse(ChainStatus chainStatus);

    List<IndexerStatusResponse> toResponseList(List<ChainStatus> chainStatuses);

    BloomStatusResponse toBloomResponse(BloomStatus bloomStatus);

    default String networkTypeToString(NetworkType networkType) {
        return networkType.name();
    }
}
