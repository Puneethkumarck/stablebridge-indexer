package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.NetworkType;
import com.stablebridge.indexer.api.WalletAddressRequest;
import com.stablebridge.indexer.api.WalletAddressResponse;
import com.stablebridge.indexer.domain.model.WalletAddress;
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

/**
 * REST controller for wallet address CRUD operations and bloom filter management.
 *
 * <p>All endpoints require API key authentication via the {@code X-API-Key} header,
 * enforced by {@link com.stablebridge.indexer.infrastructure.security.ApiKeyAuthFilter}.
 *
 * <p>Delegates directly to the domain {@link WalletCommandHandler} — no intermediate
 * application service layer (per coding standards).
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletCommandHandler walletCommandHandler;
    private final WalletControllerMapper mapper;

    /**
     * Adds a single wallet address.
     *
     * @param request the wallet address request containing address, networkType, and optional label
     * @return the created wallet address response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletAddressResponse addWallet(@Valid @RequestBody WalletAddressRequest request) {
        com.stablebridge.indexer.domain.model.NetworkType domainNetworkType =
                mapper.toDomain(request.networkType());
        WalletAddress saved = walletCommandHandler.addWallet(
                request.address(), domainNetworkType, request.label());
        return mapper.toResponse(saved);
    }

    /**
     * Adds multiple wallet addresses in batch.
     *
     * @param requests the list of wallet address requests
     * @return the list of created wallet address responses
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<WalletAddressResponse> addWalletsBatch(
            @Valid @RequestBody List<@Valid WalletAddressRequest> requests) {
        List<WalletAddressTuple> tuples = requests.stream()
                .map(req -> new WalletAddressTuple(
                        req.address(), mapper.toDomain(req.networkType()), req.label()))
                .toList();
        List<WalletAddress> saved = walletCommandHandler.addWalletsBatch(tuples);
        return saved.stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Lists all wallet addresses for the given network type.
     *
     * @param networkType the network type to filter by (required)
     * @return the list of wallet address responses
     */
    @GetMapping
    public List<WalletAddressResponse> listWallets(@RequestParam NetworkType networkType) {
        com.stablebridge.indexer.domain.model.NetworkType domainNetworkType =
                mapper.toDomain(networkType);
        List<WalletAddress> wallets = walletCommandHandler.listWallets(domainNetworkType);
        return wallets.stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Removes a wallet address by its address string and network type.
     *
     * @param address     the wallet address string to remove
     * @param networkType the network type scope
     */
    @DeleteMapping("/{address}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeWallet(
            @PathVariable String address,
            @RequestParam NetworkType networkType) {
        com.stablebridge.indexer.domain.model.NetworkType domainNetworkType =
                mapper.toDomain(networkType);
        walletCommandHandler.removeWallet(address, domainNetworkType);
    }

    /**
     * Rebuilds all bloom filters from the database.
     */
    @PostMapping("/bloom/rebuild")
    public void rebuildBloom() {
        walletCommandHandler.rebuildBloom();
    }
}
