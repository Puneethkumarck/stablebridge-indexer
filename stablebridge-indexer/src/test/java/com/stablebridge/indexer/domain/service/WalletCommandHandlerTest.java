package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.domain.service.WalletCommandHandler.WalletAddressTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_ADDRESS;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_LABEL;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.aWalletAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletCommandHandler")
class WalletCommandHandlerTest {

    @Mock
    private WalletAddressRepository walletAddressRepository;

    @Mock
    private AddressFilter addressFilter;

    @InjectMocks
    private WalletCommandHandler walletCommandHandler;

    @Nested
    @DisplayName("addWallet")
    class AddWallet {

        @Test
        @DisplayName("saves wallet to repository and adds to bloom filter")
        void savesWalletToRepositoryAndAddsToBloomFilter() {
            WalletAddress walletToSave = WalletAddress.builder()
                    .address(DEFAULT_ADDRESS)
                    .networkType(NetworkType.EVM)
                    .label(DEFAULT_LABEL)
                    .active(true)
                    .build();
            WalletAddress savedWallet = aWalletAddress().build();

            given(walletAddressRepository.save(walletToSave)).willReturn(savedWallet);

            WalletAddress result = walletCommandHandler.addWallet(
                    DEFAULT_ADDRESS, NetworkType.EVM, DEFAULT_LABEL);

            assertThat(result).usingRecursiveComparison().isEqualTo(savedWallet);
        }

        @Test
        @DisplayName("adds address to bloom filter after saving to repository")
        void addsAddressToBloomFilter() {
            WalletAddress walletToSave = WalletAddress.builder()
                    .address(DEFAULT_ADDRESS)
                    .networkType(NetworkType.EVM)
                    .label(DEFAULT_LABEL)
                    .active(true)
                    .build();
            WalletAddress savedWallet = aWalletAddress().build();

            given(walletAddressRepository.save(walletToSave)).willReturn(savedWallet);

            walletCommandHandler.addWallet(DEFAULT_ADDRESS, NetworkType.EVM, DEFAULT_LABEL);

            then(addressFilter).should().add(DEFAULT_ADDRESS, NetworkType.EVM);
        }
    }

    @Nested
    @DisplayName("addWalletsBatch")
    class AddWalletsBatch {

        @Test
        @DisplayName("saves all wallets and returns saved list")
        void savesAllWalletsAndReturnsSavedList() {
            WalletAddress walletToSave = WalletAddress.builder()
                    .address(DEFAULT_ADDRESS)
                    .networkType(NetworkType.EVM)
                    .label(DEFAULT_LABEL)
                    .active(true)
                    .build();
            WalletAddress savedWallet = aWalletAddress().build();

            given(walletAddressRepository.save(walletToSave)).willReturn(savedWallet);

            List<WalletAddressTuple> requests = List.of(
                    new WalletAddressTuple(DEFAULT_ADDRESS, NetworkType.EVM, DEFAULT_LABEL));

            List<WalletAddress> results = walletCommandHandler.addWalletsBatch(requests);

            assertThat(results)
                    .hasSize(1)
                    .first()
                    .usingRecursiveComparison()
                    .isEqualTo(savedWallet);
        }
    }

    @Nested
    @DisplayName("listWallets")
    class ListWallets {

        @Test
        @DisplayName("returns all wallets for the given network type")
        void returnsAllWalletsForNetworkType() {
            WalletAddress wallet = aWalletAddress().build();
            List<WalletAddress> expectedWallets = List.of(wallet);

            given(walletAddressRepository.findAllByNetworkType(NetworkType.EVM))
                    .willReturn(expectedWallets);

            List<WalletAddress> results = walletCommandHandler.listWallets(NetworkType.EVM);

            assertThat(results).usingRecursiveComparison().isEqualTo(expectedWallets);
        }
    }

    @Nested
    @DisplayName("removeWallet")
    class RemoveWallet {

        @Test
        @DisplayName("deletes from repository and removes from bloom filter")
        void deletesFromRepositoryAndRemovesFromBloomFilter() {
            walletCommandHandler.removeWallet(DEFAULT_ADDRESS, NetworkType.EVM);

            then(walletAddressRepository).should()
                    .deleteByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM);
            then(addressFilter).should()
                    .remove(DEFAULT_ADDRESS, NetworkType.EVM);
        }
    }

    @Nested
    @DisplayName("rebuildBloom")
    class RebuildBloom {

        @Test
        @DisplayName("loads all addresses from DB and adds each to bloom filter for all network types")
        void loadsAllAddressesAndAddsToBloom() {
            WalletAddress evmWallet = aWalletAddress().build();
            given(walletAddressRepository.findAllByNetworkType(NetworkType.EVM))
                    .willReturn(List.of(evmWallet));
            given(walletAddressRepository.findAllByNetworkType(NetworkType.SOLANA))
                    .willReturn(List.of());
            given(walletAddressRepository.findAllByNetworkType(NetworkType.BITCOIN))
                    .willReturn(List.of());

            walletCommandHandler.rebuildBloom();

            then(addressFilter).should().add(DEFAULT_ADDRESS, NetworkType.EVM);
        }
    }
}
