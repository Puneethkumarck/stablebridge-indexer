package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.BITCOIN_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BitcoinTransferParser")
class BitcoinTransferParserTest {

    private static final String BLOCK_HASH = "0000000000000000000320283a032748cef8227873ff4872689bf23f1cda83a5";
    private static final long BLOCK_HEIGHT = 840_000L;
    private static final long BLOCK_TIME = 1_713_571_767L;
    private static final Instant BLOCK_TIMESTAMP = Instant.ofEpochSecond(BLOCK_TIME);
    private static final int CONFIRMATIONS = 100;

    private static final String SENDER_ADDRESS = "1SenderAddressXyz";
    private static final String RECEIVER_ADDRESS_1 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
    private static final String RECEIVER_ADDRESS_2 = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2";

    private final BitcoinTransferParser parser = new BitcoinTransferParser(BITCOIN_CHAIN);

    @Nested
    @DisplayName("parseTransfers")
    class ParseTransfers {

        @Test
        @DisplayName("parses single P2PKH output with correct BTC to satoshis conversion")
        void parsesSingleP2pkhOutput() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_001", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            var expected = Transfer.builder()
                    .txHash("tx_001")
                    .fromAddress(SENDER_ADDRESS)
                    .toAddress(RECEIVER_ADDRESS_1)
                    .rawAmount("50000000")
                    .amount(new BigDecimal("0.50000000"))
                    .decimals(8)
                    .tokenSymbol("BTC")
                    .tokenContractAddress(null)
                    .blockNumber(BLOCK_HEIGHT)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(0)
                    .chainId(BITCOIN_CHAIN)
                    .timestamp(BLOCK_TIMESTAMP)
                    .nativeTransfer(true)
                    .build();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("parses multiple outputs from a single transaction")
        void parsesMultipleOutputsFromSingleTransaction() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_002", SENDER_ADDRESS, List.of(
                            aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1),
                            aVout(new BigDecimal("0.49990000"), 1, "pubkeyhash", RECEIVER_ADDRESS_2)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).toAddress()).isEqualTo(RECEIVER_ADDRESS_1);
            assertThat(result.get(0).rawAmount()).isEqualTo("50000000");
            assertThat(result.get(1).toAddress()).isEqualTo(RECEIVER_ADDRESS_2);
            assertThat(result.get(1).rawAmount()).isEqualTo("49990000");
        }

        @Test
        @DisplayName("parses multiple transactions in a block with correct transaction indices")
        void parsesMultipleTransactionsInBlock() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_001", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1))),
                    aTransferTransaction("tx_002", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("2.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_2)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).transactionIndex()).isEqualTo(0);
            assertThat(result.get(0).txHash()).isEqualTo("tx_001");
            assertThat(result.get(1).transactionIndex()).isEqualTo(1);
            assertThat(result.get(1).txHash()).isEqualTo("tx_002");
        }

        @Test
        @DisplayName("skips coinbase transactions with null txid in vin")
        void skipsCoinbaseTransactionsWithNullTxid() {
            // given
            var coinbaseTx = BtcTransaction.builder()
                    .txid("tx_coinbase")
                    .vin(List.of(BtcVin.builder()
                            .txid(null)
                            .vout(0)
                            .scriptSig(BtcScriptSig.builder().asm("OP_0 coinbase").hex("03a0d20c").build())
                            .build()))
                    .vout(List.of(aVout(new BigDecimal("6.25000000"), 0, "scripthash", "3MinerAddress")))
                    .build();

            var regularTx = aTransferTransaction("tx_regular", SENDER_ADDRESS,
                    List.of(aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)));

            var block = aBlockWithTransactions(List.of(coinbaseTx, regularTx));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().txHash()).isEqualTo("tx_regular");
        }

        @Test
        @DisplayName("skips coinbase transactions with coinbase field set")
        void skipsCoinbaseTransactionsWithCoinbaseField() {
            // given
            var coinbaseTx = BtcTransaction.builder()
                    .txid("tx_coinbase")
                    .vin(List.of(BtcVin.builder()
                            .coinbase("03a0d20c")
                            .vout(0xFFFFFFFF)
                            .build()))
                    .vout(List.of(aVout(new BigDecimal("6.25000000"), 0, "scripthash", "3MinerAddress")))
                    .build();

            var block = aBlockWithTransactions(List.of(coinbaseTx));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips vouts with nulldata script type (OP_RETURN)")
        void skipsNulldataOutputs() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_003", SENDER_ADDRESS, List.of(
                            aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1),
                            BtcVout.builder()
                                    .value(BigDecimal.ZERO)
                                    .n(1)
                                    .scriptPubKey(BtcScriptPubKey.builder()
                                            .asm("OP_RETURN data")
                                            .hex("6a")
                                            .type("nulldata")
                                            .build())
                                    .build()))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().toAddress()).isEqualTo(RECEIVER_ADDRESS_1);
        }

        @Test
        @DisplayName("skips vouts with nonstandard script type")
        void skipsNonstandardOutputs() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_004", SENDER_ADDRESS, List.of(
                            BtcVout.builder()
                                    .value(new BigDecimal("0.01000000"))
                                    .n(0)
                                    .scriptPubKey(BtcScriptPubKey.builder()
                                            .asm("nonstandard")
                                            .hex("00")
                                            .type("nonstandard")
                                            .build())
                                    .build()))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips vouts with no resolvable address")
        void skipsVoutsWithNoAddress() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_005", SENDER_ADDRESS, List.of(
                            BtcVout.builder()
                                    .value(new BigDecimal("0.01000000"))
                                    .n(0)
                                    .scriptPubKey(BtcScriptPubKey.builder()
                                            .asm("some script")
                                            .hex("00")
                                            .type("pubkeyhash")
                                            .build())
                                    .build()))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips vouts with null scriptPubKey")
        void skipsVoutsWithNullScriptPubKey() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_006", SENDER_ADDRESS, List.of(
                            BtcVout.builder()
                                    .value(new BigDecimal("0.01000000"))
                                    .n(0)
                                    .scriptPubKey(null)
                                    .build()))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("address type support")
    class AddressTypeSupport {

        @Test
        @DisplayName("supports P2SH (scripthash) addresses")
        void supportsP2shAddresses() {
            // given
            var p2shAddress = "3EktnHQD7RiAE6uzMj2ZifT9YgRnMLkHnR";
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_p2sh", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "scripthash", p2shAddress)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().toAddress()).isEqualTo(p2shAddress);
        }

        @Test
        @DisplayName("supports P2WPKH (witness_v0_keyhash) addresses")
        void supportsP2wpkhAddresses() {
            // given
            var p2wpkhAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4";
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_p2wpkh", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("0.10000000"), 0, "witness_v0_keyhash", p2wpkhAddress)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().toAddress()).isEqualTo(p2wpkhAddress);
        }

        @Test
        @DisplayName("supports P2WSH (witness_v0_scripthash) addresses")
        void supportsP2wshAddresses() {
            // given
            var p2wshAddress = "bc1qrp33g0q5b5698ahp5jnf5yzjmgcev69lmxaga3rvarzr90p2wkvssp5ek7";
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_p2wsh", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("5.00000000"), 0, "witness_v0_scripthash", p2wshAddress)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().toAddress()).isEqualTo(p2wshAddress);
        }

        @Test
        @DisplayName("supports P2TR (witness_v1_taproot) addresses")
        void supportsP2trAddresses() {
            // given
            var p2trAddress = "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0";
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_p2tr", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("0.25000000"), 0, "witness_v1_taproot", p2trAddress)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().toAddress()).isEqualTo(p2trAddress);
        }
    }

    @Nested
    @DisplayName("from address resolution")
    class FromAddressResolution {

        @Test
        @DisplayName("resolves fromAddress from first vin prevout")
        void resolvesFromAddressFromPrevout() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_prevout", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().fromAddress()).isEqualTo(SENDER_ADDRESS);
        }

        @Test
        @DisplayName("sets fromAddress to null when prevout is not available")
        void setsNullFromAddressWhenNoPrevout() {
            // given
            var tx = BtcTransaction.builder()
                    .txid("tx_no_prevout")
                    .vin(List.of(BtcVin.builder()
                            .txid("prev_tx_hash")
                            .vout(0)
                            .scriptSig(BtcScriptSig.builder().asm("sig").hex("48").build())
                            .prevout(null)
                            .build()))
                    .vout(List.of(aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))
                    .build();

            var block = aBlockWithTransactions(List.of(tx));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().fromAddress()).isNull();
        }

        @Test
        @DisplayName("sets fromAddress to null when vin list is empty")
        void setsNullFromAddressWhenVinEmpty() {
            // given
            var tx = BtcTransaction.builder()
                    .txid("tx_empty_vin")
                    .vin(List.of())
                    .vout(List.of(aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))
                    .build();

            var block = aBlockWithTransactions(List.of(tx));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().fromAddress()).isNull();
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("returns empty list for empty block with no transactions")
        void returnsEmptyForEmptyBlock() {
            // given
            var block = aBlockWithTransactions(List.of());

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for block with null transactions")
        void returnsEmptyForNullTransactions() {
            // given
            var block = BtcBlock.builder()
                    .hash(BLOCK_HASH)
                    .height(BLOCK_HEIGHT)
                    .time(BLOCK_TIME)
                    .confirmations(CONFIRMATIONS)
                    .tx(null)
                    .build();

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("converts whole BTC amounts to satoshis correctly")
        void convertsWholeBtcToSatoshis() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_whole", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().rawAmount()).isEqualTo("100000000");
        }

        @Test
        @DisplayName("converts fractional BTC amounts to satoshis correctly")
        void convertsFractionalBtcToSatoshis() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_frac", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("0.00000001"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().rawAmount()).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("transfer properties")
    class TransferProperties {

        @Test
        @DisplayName("sets tokenSymbol to BTC")
        void setsTokenSymbolToBtc() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_sym", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result.getFirst().tokenSymbol()).isEqualTo("BTC");
        }

        @Test
        @DisplayName("sets decimals to 8")
        void setsDecimalsToEight() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_dec", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result.getFirst().decimals()).isEqualTo(8);
        }

        @Test
        @DisplayName("sets nativeTransfer to true")
        void setsNativeTransferToTrue() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_nat", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result.getFirst().nativeTransfer()).isTrue();
        }

        @Test
        @DisplayName("sets tokenContractAddress to null")
        void setsTokenContractAddressToNull() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_null_contract", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result.getFirst().tokenContractAddress()).isNull();
        }

        @Test
        @DisplayName("sets chainId to BITCOIN_CHAIN")
        void setsChainIdToBitcoinChain() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_chain", SENDER_ADDRESS,
                            List.of(aVout(new BigDecimal("1.00000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result.getFirst().chainId()).isEqualTo(BITCOIN_CHAIN);
        }

        @Test
        @DisplayName("uses vout.n as logIndex")
        void usesVoutNAsLogIndex() {
            // given
            var block = aBlockWithTransactions(List.of(
                    aTransferTransaction("tx_log", SENDER_ADDRESS, List.of(
                            aVout(new BigDecimal("0.50000000"), 0, "pubkeyhash", RECEIVER_ADDRESS_1),
                            aVout(new BigDecimal("0.49990000"), 3, "pubkeyhash", RECEIVER_ADDRESS_2)))));

            // when
            var result = parser.parseTransfers(block);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).logIndex()).isEqualTo(0);
            assertThat(result.get(1).logIndex()).isEqualTo(3);
        }
    }

    // --- Test helper methods (ACL DTOs are package-private, helpers stay in test class) ---

    private static BtcBlock aBlockWithTransactions(List<BtcTransaction> transactions) {
        return BtcBlock.builder()
                .hash(BLOCK_HASH)
                .height(BLOCK_HEIGHT)
                .time(BLOCK_TIME)
                .confirmations(CONFIRMATIONS)
                .tx(transactions)
                .build();
    }

    private static BtcTransaction aTransferTransaction(String txid, String senderAddress, List<BtcVout> vouts) {
        var prevout = BtcVout.builder()
                .value(BigDecimal.ZERO)
                .n(0)
                .scriptPubKey(BtcScriptPubKey.builder()
                        .type("pubkeyhash")
                        .address(senderAddress)
                        .build())
                .build();

        return BtcTransaction.builder()
                .txid(txid)
                .vin(List.of(BtcVin.builder()
                        .txid("prev_tx_hash")
                        .vout(0)
                        .scriptSig(BtcScriptSig.builder().asm("sig pubkey").hex("4830").build())
                        .prevout(prevout)
                        .build()))
                .vout(vouts)
                .build();
    }

    private static BtcVout aVout(BigDecimal value, int n, String scriptType, String address) {
        return BtcVout.builder()
                .value(value)
                .n(n)
                .scriptPubKey(BtcScriptPubKey.builder()
                        .asm("script " + scriptType)
                        .hex("00")
                        .type(scriptType)
                        .address(address)
                        .build())
                .build();
    }
}
