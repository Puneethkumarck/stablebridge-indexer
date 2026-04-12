package com.stablebridge.indexer.infrastructure.chain.tron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record TronTransaction(
        String txID,
        List<RetResult> ret,
        RawData raw_data) {

    boolean isSuccessful() {
        return ret != null && !ret.isEmpty()
                && "SUCCESS".equals(ret.getFirst().contractRet());
    }

    boolean isNativeTransfer() {
        return raw_data != null && raw_data.contract() != null && !raw_data.contract().isEmpty()
                && "TransferContract".equals(raw_data.contract().getFirst().type());
    }

    String ownerAddress() {
        return raw_data != null && raw_data.contract() != null && !raw_data.contract().isEmpty()
                && raw_data.contract().getFirst().parameter() != null
                && raw_data.contract().getFirst().parameter().value() != null
                ? raw_data.contract().getFirst().parameter().value().owner_address() : null;
    }

    String toAddress() {
        return raw_data != null && raw_data.contract() != null && !raw_data.contract().isEmpty()
                && raw_data.contract().getFirst().parameter() != null
                && raw_data.contract().getFirst().parameter().value() != null
                ? raw_data.contract().getFirst().parameter().value().to_address() : null;
    }

    long amount() {
        if (raw_data == null || raw_data.contract() == null || raw_data.contract().isEmpty()) {
            return 0;
        }
        var param = raw_data.contract().getFirst().parameter();
        if (param == null || param.value() == null || param.value().amount() == null) {
            return 0;
        }
        return param.value().amount();
    }

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RetResult(String contractRet) {}

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawData(List<Contract> contract) {}

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contract(String type, ContractParameter parameter) {}

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContractParameter(ContractValue value, String type_url) {}

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContractValue(
            String owner_address,
            String to_address,
            Long amount,
            String contract_address,
            String data) {}
}
