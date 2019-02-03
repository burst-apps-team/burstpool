CREATE TABLE miners (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  pending_balance DOUBLE,
  estimated_capacity DOUBLE,
  share DOUBLE,
  hitSum DOUBLE,
  name TEXT,
  user_agent TEXT,
  PRIMARY KEY (db_id)
);

CREATE TABLE minerDeadlines (
  db_id BIGINT AUTO_INCREMENT,
  account_id BIGINT,
  height LONG,
  deadline BIGINT,
  baseTarget BIGINT,
  PRIMARY KEY (db_id)
);

CREATE TABLE bestSubmissions (
  db_id BIGINT AUTO_INCREMENT,
  height LONG,
  accountId BIGINT,
  nonce TEXT,
  PRIMARY KEY (db_id)
);

CREATE TABLE poolState (
  db_id BIGINT AUTO_INCREMENT,
  key TEXT,
  value TEXT,
  PRIMARY KEY (db_id)
);