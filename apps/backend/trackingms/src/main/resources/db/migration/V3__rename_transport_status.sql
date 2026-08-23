-- 追跡の状態の列名を、Tracking Context の言葉に合わせる（IT7 計画の注 4）。
--
-- transport_status は設計では **Booking Context の Delivery が持つ名前**であり、
-- この表に置くと BC をまたいで同じ名前が別物を指す。ドメインの型を TrackingStatus へ
-- 改名したので、列も同じ変更で合わせる——片方だけ直すと、読むたびに対応表が要る。
ALTER TABLE tracking_activity RENAME COLUMN transport_status TO tracking_status;
