CREATE TABLE miners (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  pending_balance DOUBLE,
  estimated_capacity DOUBLE,
  share DOUBLE,
  minimum_payout DOUBLE,
  name TEXT,
  user_agent TEXT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE miner_deadlines (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  height BIGINT,
  deadline BIGINT,
  base_target BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE best_submissions (
  db_id BIGINT AUTO_INCREMENT,
  height BIGINT,
  account_id BIGINT,
  nonce TEXT,
  deadline BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pool_state (
  `key` VARCHAR(50),
  value TEXT,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE won_blocks (
  db_id BIGINT AUTO_INCREMENT,
  block_height BIGINT,
  block_id BIGINT,
  generator_id BIGINT,
  nonce TEXT,
  full_reward BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payouts (
  db_id BIGINT AUTO_INCREMENT,
  transaction_id BIGINT,
  sender_public_key BINARY,
  fee BIGINT,
  deadline BIGINT,
  attachment BINARY,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
