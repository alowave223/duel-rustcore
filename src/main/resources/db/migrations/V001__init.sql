CREATE TABLE IF NOT EXISTS schema_version (
    version     INT           NOT NULL PRIMARY KEY,
    applied_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255)  NOT NULL
);

CREATE TABLE IF NOT EXISTS duels_stats (
    mode_id          VARCHAR(64)  NOT NULL,
    player_uuid      CHAR(36)     NOT NULL,
    wins             INT          NOT NULL DEFAULT 0,
    losses           INT          NOT NULL DEFAULT 0,
    kills            INT          NOT NULL DEFAULT 0,
    deaths           INT          NOT NULL DEFAULT 0,
    win_streak       INT          NOT NULL DEFAULT 0,
    best_win_streak  INT          NOT NULL DEFAULT 0,
    elo              INT          NOT NULL DEFAULT 1000,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (mode_id, player_uuid),
    INDEX idx_stats_mode_elo (mode_id, elo DESC),
    INDEX idx_stats_mode_wins (mode_id, wins DESC)
);

CREATE TABLE IF NOT EXISTS duels_friends (
    player_a CHAR(36) NOT NULL,
    player_b CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_a, player_b),
    INDEX idx_friends_b (player_b)
);

CREATE TABLE IF NOT EXISTS duels_settings (
    player_uuid           CHAR(36)    NOT NULL PRIMARY KEY,
    party_invites         VARCHAR(16) NOT NULL DEFAULT 'ALL',
    challenges            VARCHAR(16) NOT NULL DEFAULT 'ALL',
    accept_friend_requests TINYINT(1) NOT NULL DEFAULT 1,
    status                VARCHAR(16) NOT NULL DEFAULT 'ONLINE',
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS duels_kit_layouts (
    player_uuid CHAR(36)    NOT NULL,
    mode_id     VARCHAR(64) NOT NULL,
    src_slot    INT         NOT NULL,
    dst_slot    INT         NOT NULL,
    PRIMARY KEY (player_uuid, mode_id, src_slot)
);

CREATE TABLE IF NOT EXISTS duels_ranked_prefs (
    player_uuid CHAR(36)   NOT NULL PRIMARY KEY,
    ranked      TINYINT(1) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
