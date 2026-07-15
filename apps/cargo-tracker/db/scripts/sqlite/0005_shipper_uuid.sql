-- shipper に ShipperId（Guid）を業務識別子として保持するカラムを追加（ADR-0008）。SQLite 方言。
-- Booking Context の ShipperExistenceChecker ACL が完全な Guid で存在確認できるようにする。
ALTER TABLE shipper ADD COLUMN shipper_uuid TEXT;
