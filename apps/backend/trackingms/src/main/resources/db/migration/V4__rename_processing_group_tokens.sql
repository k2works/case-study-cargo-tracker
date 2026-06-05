-- ADR-0016 / IT8 T1.2：@ProcessingGroup 改名に伴う tokenentry のキー移行（trackingms）

UPDATE tokenentry SET processorname = 'local-tracking-summary-projection'
WHERE processorname = 'tracking-local-projection';

UPDATE tokenentry SET processorname = 'cross-tracking-issuance-requests'
WHERE processorname = 'tracking-issuance-requests';

UPDATE tokenentry SET processorname = 'local-tracking-notifications'
WHERE processorname = 'tracking-notifications';

UPDATE tokenentry SET processorname = 'cross-handling-activity-events'
WHERE processorname = 'handling-activity-events';

-- 注: local-tracking-exception-projection は IT6 で既に新規約準拠（改名不要）。
