CREATE SCHEMA BurstPool;

CREATE TABLE BurstPool.miners (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  pending_balance DOUBLE,
  estimated_capacity DOUBLE,
  share DOUBLE,
  hitSum DOUBLE,
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
  PRIMARY KEY (db_id)
);

CREATE TABLE BurstPool.poolState (
  key VARCHAR(50),
  value TEXT,
  PRIMARY KEY (key)
);