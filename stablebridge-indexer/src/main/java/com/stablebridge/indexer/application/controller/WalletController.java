package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.NetworkType;
import com.stablebridge.indexer.api.WalletAddressRequest;
import com.stablebridge.indexer.api.WalletAddressResponse;
import com.stablebridge.indexer.domain.service.WalletCommandHandler;
import com.stablebridge.indexer.domain.service.WalletCommandHandler.WalletAddressTuple;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletCommandHandler walletCommandHandler;
    private final WalletControllerMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletAddressResponse addWallet(@Valid @RequestBody WalletAddressRequest request) {
        var domainNetworkType = mapper.toDomain(request.networkType());
        var saved = walletCommandHandler.addWallet(
                request.address(), domainNetworkType, request.label());
        return mapper.toResponse(saved);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<WalletAddressResponse> addWalletsBatch(
            @Valid @RequestBody List<@Valid WalletAddressRequest> requests) {
        var tuples = requests.stream()
                .map(req -> new WalletAddressTuple(
                        req.address(), mapper.toDomain(req.networkType()), req.label()))
                .toList();
        var saved = walletCommandHandler.addWalletsBatch(tuples);
        return saved.stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping
    public List<WalletAddressResponse> listWallets(@RequestParam NetworkType networkType) {
        var domainNetworkType = mapper.toDomain(networkType);
        var wallets = walletCommandHandler.listWallets(domainNetworkType);
        return wallets.stream()
                .map(mapper::toResponse)
                .toList();
    }

    @DeleteMapping("/{address}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeWallet(
            @PathVariable String address,
            @RequestParam NetworkType networkType) {
        var domainNetworkType = mapper.toDomain(networkType);
        walletCommandHandler.removeWallet(address, domainNetworkType);
    }

    @PostMapping("/bloom/rebuild")
    public void rebuildBloom() {
        walletCommandHandler.rebuildBloom();
    }
}
