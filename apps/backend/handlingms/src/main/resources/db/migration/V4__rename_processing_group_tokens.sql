-- ADR-0016 / IT8 T1.2：@ProcessingGroup 改名に伴う tokenentry のキー移行（handlingms）

UPDATE tokenentry SET processorname = 'outbound-handling-activity-events'
WHERE processorname = 'handling-cross-service-publish';

UPDATE tokenentry SET processorname = 'cross-cargo-snapshot'
WHERE processorname = 'cargo-snapshot';
