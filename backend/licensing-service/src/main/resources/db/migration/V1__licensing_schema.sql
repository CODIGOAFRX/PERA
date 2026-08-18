CREATE TABLE licenses (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    activation_code_hash BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    grace_period_seconds BIGINT NOT NULL,
    max_installations INTEGER NOT NULL,
    check_interval_seconds BIGINT NOT NULL,
    first_activated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_license_activation_hash UNIQUE (activation_code_hash),
    CONSTRAINT uk_license_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_license_activation_hash_length CHECK (octet_length(activation_code_hash) = 32),
    CONSTRAINT ck_license_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_license_validity CHECK (valid_until > valid_from),
    CONSTRAINT ck_license_grace CHECK (grace_period_seconds BETWEEN 0 AND 31536000),
    CONSTRAINT ck_license_installation_limit CHECK (max_installations BETWEEN 1 AND 10000),
    CONSTRAINT ck_license_check_interval CHECK (check_interval_seconds BETWEEN 60 AND 604800)
);

CREATE INDEX idx_licenses_company_created ON licenses (company_id, created_at DESC);
CREATE INDEX idx_licenses_company_status ON licenses (company_id, status);

CREATE TABLE license_features (
    license_id UUID NOT NULL,
    feature_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (license_id, feature_code),
    CONSTRAINT fk_license_feature_license FOREIGN KEY (license_id) REFERENCES licenses (id) ON DELETE CASCADE,
    CONSTRAINT ck_license_feature_code CHECK (feature_code ~ '^[a-z0-9][a-z0-9._:-]{0,63}$')
);

CREATE TABLE license_installations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    license_id UUID NOT NULL,
    installation_fingerprint_hash BYTEA NOT NULL,
    token_hash BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL,
    token_issued_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    last_validated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_license_installation_license FOREIGN KEY (company_id, license_id)
        REFERENCES licenses (company_id, id) ON DELETE CASCADE,
    CONSTRAINT uk_license_installation_fingerprint UNIQUE (license_id, installation_fingerprint_hash),
    CONSTRAINT uk_license_installation_token UNIQUE (token_hash),
    CONSTRAINT ck_license_installation_fingerprint_hash_length
        CHECK (octet_length(installation_fingerprint_hash) = 32),
    CONSTRAINT ck_license_installation_token_hash_length CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_license_installation_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_license_installation_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_license_installations_company_license
    ON license_installations (company_id, license_id, activated_at);
CREATE INDEX idx_license_installations_active
    ON license_installations (license_id, status);
