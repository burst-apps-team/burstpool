CREATE TABLE miners (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  pending_balance BIGINT,
  estimated_capacity DOUBLE,
  share DOUBLE,
  minimum_payout BIGINT,
  name TEXT,
  user_agent TEXT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX miners_index ON miners (account_id);

CREATE TABLE miner_deadlines (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  height BIGINT,
  deadline BIGINT,
  base_target BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX miner_deadlines_index ON miner_deadlines (account_id, height);

CREATE TABLE best_submissions (
  db_id BIGINT AUTO_INCREMENT,
  height BIGINT,
  account_id BIGINT,
  nonce TEXT,
  deadline BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX best_submissions_index ON best_submissions (height);

CREATE TABLE pool_state (
  `key` VARCHAR(50),
  value TEXT,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX pool_state_index ON pool_state (`key`);

CREATE TABLE won_blocks (
  db_id BIGINT AUTO_INCREMENT,
  block_height BIGINT,
  block_id BIGINT,
  generator_id BIGINT,
  nonce TEXT,
  full_reward BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX won_blocks_index ON won_blocks (block_height, block_id);

CREATE TABLE payouts (
  db_id BIGINT AUTO_INCREMENT,
  transaction_id BIGINT,
  sender_public_key BINARY(32),
  fee BIGINT,
  deadline BIGINT,
  attachment BLOB,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX payouts_index ON payouts (transaction_id);
