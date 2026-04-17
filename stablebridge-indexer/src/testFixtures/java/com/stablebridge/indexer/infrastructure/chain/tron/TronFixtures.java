package com.stablebridge.indexer.infrastructure.chain.tron;

import java.time.Instant;
import java.util.List;

public final class TronFixtures {

    public static final String SOME_FROM_BASE58 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
    public static final String SOME_FROM_HEX = "41a614f803b6fd780986a42c78ec9c7f77e6ded13c";
    public static final String SOME_TO_BASE58 = "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7";
    public static final String SOME_TO_HEX = "4174472e7d35395a6b5add427eecb7f4b62ad2b071";
    public static final String SOME_TX_HASH =
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    public static final String SOME_BLOCK_ID =
            "0000000003b0b2b71234567890abcdef1234567890abcdef1234567890abcdef";
    public static final long SOME_BLOCK_NUMBER = 62_000_823L;
    public static final long SOME_BLOCK_TIMESTAMP_MILLIS = 1_700_000_000_000L;
    public static final Instant SOME_BLOCK_TIMESTAMP =
            Instant.ofEpochMilli(SOME_BLOCK_TIMESTAMP_MILLIS);
    public static final long SOME_AMOUNT_SUN = 1_000_000L; // 1 TRX
    public static final String SOME_PARENT_HASH =
            "0000000003b0b2b61234567890abcdef1234567890abcdef1234567890abcdef";

    private TronFixtures() {
        // fixture class
    }

    public static TronBlock.TronBlockBuilder aTronBlock() {
        return TronBlock.builder()
                .blockID(SOME_BLOCK_ID)
                .block_header(aBlockHeader().build())
                .transactions(List.of(aTronTransferContractTransaction().build()));
    }

    public static TronBlock.BlockHeader.BlockHeaderBuilder aBlockHeader() {
        return TronBlock.BlockHeader.builder()
                .raw_data(aRawBlockData().build());
    }

    public static TronBlock.RawData.RawDataBuilder aRawBlockData() {
        return TronBlock.RawData.builder()
                .number(SOME_BLOCK_NUMBER)
                .timestamp(SOME_BLOCK_TIMESTAMP_MILLIS)
                .parentHash(SOME_PARENT_HASH);
    }

    public static TronTransaction.TronTransactionBuilder aTronTransferContractTransaction() {
        return aTronTransaction(SOME_FROM_BASE58, SOME_TO_BASE58, SOME_AMOUNT_SUN);
    }

    public static TronTransaction.TronTransactionBuilder aTronTransaction(
            String ownerAddress, String toAddress, Long amountSun) {
        return aTronTransactionWithType("TransferContract", ownerAddress, toAddress, amountSun);
    }

    public static TronTransaction.TronTransactionBuilder aTronTransactionWithType(
            String contractType, String ownerAddress, String toAddress, Long amountSun) {
        var value = TronTransaction.ContractValue.builder()
                .owner_address(ownerAddress)
                .to_address(toAddress)
                .amount(amountSun)
                .build();

        var parameter = TronTransaction.ContractParameter.builder()
                .value(value)
                .type_url("type.googleapis.com/protocol." + contractType)
                .build();

        var contract = TronTransaction.Contract.builder()
                .type(contractType)
                .parameter(parameter)
                .build();

        var rawData = TronTransaction.RawData.builder()
                .contract(List.of(contract))
                .build();

        var ret = TronTransaction.RetResult.builder()
                .contractRet("SUCCESS")
                .build();

        return TronTransaction.builder()
                .txID(SOME_TX_HASH)
                .ret(List.of(ret))
                .raw_data(rawData);
    }

    public static TronTransaction.TronTransactionBuilder aNonTransferContractTransaction() {
        return aTronTransactionWithType(
                "TriggerSmartContract", SOME_FROM_BASE58, SOME_TO_BASE58, SOME_AMOUNT_SUN);
    }
}
