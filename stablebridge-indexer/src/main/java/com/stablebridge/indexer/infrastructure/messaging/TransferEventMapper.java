package com.stablebridge.indexer.infrastructure.messaging;

import com.stablebridge.indexer.api.TransferEvent;
import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface TransferEventMapper {

    @Mapping(source = "transfer.txHash", target = "txHash")
    @Mapping(source = "transfer.fromAddress", target = "fromAddress")
    @Mapping(source = "transfer.toAddress", target = "toAddress")
    @Mapping(source = "transfer.rawAmount", target = "rawAmount")
    @Mapping(source = "transfer.amount", target = "amount")
    @Mapping(source = "transfer.decimals", target = "decimals")
    @Mapping(source = "transfer.tokenSymbol", target = "tokenSymbol")
    @Mapping(source = "transfer.tokenContractAddress", target = "tokenContractAddress")
    @Mapping(source = "transfer.blockNumber", target = "blockNumber")
    @Mapping(source = "transfer.blockHash", target = "blockHash")
    @Mapping(source = "transfer.transactionIndex", target = "transactionIndex")
    @Mapping(source = "transfer.logIndex", target = "logIndex")
    @Mapping(target = "chainId", expression = "java(event.transfer().chainId().name())")
    @Mapping(target = "networkType", expression = "java(event.transfer().chainId().networkType().name())")
    @Mapping(source = "transfer.timestamp", target = "timestamp")
    @Mapping(source = "transfer.nativeTransfer", target = "nativeTransfer")
    @Mapping(source = "detectedAt", target = "detectedAt")
    TransferEvent toTransferEvent(TransferDetectedEvent event);
}
