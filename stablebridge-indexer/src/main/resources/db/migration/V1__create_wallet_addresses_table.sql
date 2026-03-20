-- ============================================================================
-- V1: Create wallet_addresses table
--
-- PostgreSQL is used ONLY for wallet addresses (per Architecture Decision 5).
-- Block progress, failed blocks, and catchup ranges are stored in Redis.
--
-- Wallet registration scope is per-network-type (EVM/SOLANA/BITCOIN),
-- not per-chain (per Architecture Decision 10). One registration covers
-- all chains of that network type.
-- ============================================================================

CREATE TABLE wallet_addresses (
    id              BIGSERIAL                   PRIMARY KEY,
    address         VARCHAR(255)                NOT NULL,
    network_type    VARCHAR(20)                 NOT NULL,
    label           VARCHAR(255),
    active          BOOLEAN                     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

-- One entry per address per network type (Decision 10: per-network-type scope)
ALTER TABLE wallet_addresses
    ADD CONSTRAINT uq_wallet_addresses_address_network_type
        UNIQUE (address, network_type);

-- Bulk loading all addresses for a network type into the bloom filter at startup
CREATE INDEX idx_wallet_addresses_network_type
    ON wallet_addresses (network_type);

-- Filtering active/inactive addresses
CREATE INDEX idx_wallet_addresses_active
    ON wallet_addresses (active);

-- CHECK constraint to enforce valid network_type values
ALTER TABLE wallet_addresses
    ADD CONSTRAINT chk_wallet_addresses_network_type
        CHECK (network_type IN ('EVM', 'SOLANA', 'BITCOIN'));
