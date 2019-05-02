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

CREATE TABLE minerDeadlines (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  height BIGINT,
  deadline BIGINT,
  baseTarget BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bestSubmissions (
  db_id BIGINT AUTO_INCREMENT,
  height BIGINT,
  accountId BIGINT,
  nonce TEXT,
  deadline BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE poolState (
  `key` VARCHAR(50),
  value TEXT,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wonBlocks (
  db_id BIGINT AUTO_INCREMENT,
  blockHeight BIGINT,
  blockId BIGINT,
  generatorId BIGINT,
  nonce TEXT,
  fullReward BIGINT,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payouts (
  db_id BIGINT AUTO_INCREMENT,
  transactionId BIGINT,
  senderPublicKey BINARY,
  fee BIGINT,
  deadline BIGINT,
  attachment BINARY,
  PRIMARY KEY (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
