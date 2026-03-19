package com.stablebridge.indexer.client;

import com.stablebridge.indexer.api.IndexerStatusResponse;
import com.stablebridge.indexer.api.NetworkType;
import com.stablebridge.indexer.api.WalletAddressRequest;
import com.stablebridge.indexer.api.WalletAddressResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "indexer-client", url = "${indexer.client.url}")
public interface IndexerClient {

    @PostMapping("/api/v1/wallets")
    WalletAddressResponse registerWallet(@RequestBody WalletAddressRequest request);

    @GetMapping("/api/v1/wallets/{address}")
    WalletAddressResponse getWallet(
            @PathVariable String address, @RequestParam NetworkType networkType);

    @DeleteMapping("/api/v1/wallets/{address}")
    void deleteWallet(@PathVariable String address, @RequestParam NetworkType networkType);

    @GetMapping("/api/v1/wallets")
    List<WalletAddressResponse> listWallets(@RequestParam NetworkType networkType);

    @GetMapping("/api/v1/status")
    List<IndexerStatusResponse> getStatus();
}
