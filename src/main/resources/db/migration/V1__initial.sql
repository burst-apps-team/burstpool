CREATE SCHEMA BurstPool;

CREATE TABLE BurstPool.miners (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  pending_balance DOUBLE,
  estimated_capacity DOUBLE,
  share DOUBLE,
  minimum_payout DOUBLE,
  name TEXT,
  user_agent TEXT,
  PRIMARY KEY (db_id)
);

CREATE TABLE BurstPool.minerDeadlines (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  height LONG,
  deadline BIGINT,
  baseTarget BIGINT,
  PRIMARY KEY (db_id)
);

CREATE TABLE BurstPool.bestSubmissions (
  db_id BIGINT AUTO_INCREMENT,
  height LONG,
  accountId BIGINT,
  nonce TEXT,
  deadline BIGINT,
  PRIMARY KEY (db_id)
);

CREATE TABLE BurstPool.poolState (
  key VARCHAR(50),
  value TEXT,
  PRIMARY KEY (key)
);

CREATE TABLE BurstPool.wonBlocks (
  db_id BIGINT AUTO_INCREMENT,
  blockHeight LONG,
  blockId BIGINT,
  generatorId BIGINT,
  nonce TEXT,
  fullReward BIGINT,
  PRIMARY KEY (db_id)
);

CREATE TABLE BurstPool.payouts (
  db_id BIGINT AUTO_INCREMENT,
  transactionId BIGINT,
  senderPublicKey BINARY,
  fee BIGINT,
  deadline LONG,
  attachment BINARY,
  PRIMARY KEY (db_id)
)
