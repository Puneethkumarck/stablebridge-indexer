package com.stablebridge.indexer.infrastructure.messaging;

import com.stablebridge.indexer.api.TransferEvent;
import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import com.stablebridge.indexer.domain.model.Transfer;
import com.stablebridge.indexer.testutil.TransferFixtures;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferEventMapperTest {

    private final TransferEventMapper mapper = Mappers.getMapper(TransferEventMapper.class);

    @Test
    void toTransferEvent_mapsAllFieldsCorrectly() {
        // given
        TransferDetectedEvent event = TransferFixtures.aTransferDetectedEvent().build();
        Transfer transfer = event.transfer();

        TransferEvent expected = new TransferEvent(
                transfer.txHash(),
                transfer.fromAddress(),
                transfer.toAddress(),
                transfer.rawAmount(),
                transfer.amount(),
                transfer.decimals(),
                transfer.tokenSymbol(),
                transfer.tokenContractAddress(),
                transfer.blockNumber(),
                transfer.blockHash(),
                transfer.transactionIndex(),
                transfer.logIndex(),
                "ETHEREUM",
                "EVM",
                transfer.timestamp(),
                transfer.nativeTransfer(),
                event.direction().name(),
                event.detectedAt());

        // when
        TransferEvent actual = mapper.toTransferEvent(event);

        // then
        assertThat(actual)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }
}
