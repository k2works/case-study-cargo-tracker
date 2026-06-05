-- ADR-0016 / IT8 T1.2：@ProcessingGroup 改名に伴う tokenentry のキー移行（routingms）

UPDATE tokenentry SET processorname = 'cross-route-design-requests'
WHERE processorname = 'route-design-requests';
