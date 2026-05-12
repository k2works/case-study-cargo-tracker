-- V6: shipper テーブル作成
CREATE TABLE shipper (
  id BIGSERIAL PRIMARY KEY,
  shipper_code VARCHAR(20) NOT NULL UNIQUE,
  shipper_type VARCHAR(20) NOT NULL,
  name VARCHAR(200) NOT NULL,
  email VARCHAR(200) NOT NULL,
  phone VARCHAR(50),
  contract_number VARCHAR(50),
  discount_rate NUMERIC(5,4),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
