package com.example.cargotracker.shipper.domain.model;

import com.example.cargotracker.shipper.domain.event.ShipperRegisteredEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Shipper {

    private final ShipperId id;
    private final ShipperName name;
    private final ContactInfo contactInfo;
    private final CustomerCategory category;
    private final CorporateContractInfo corporateContractInfo;
    private final List<Object> domainEvents = new ArrayList<>();

    private Shipper(ShipperId id, ShipperName name, ContactInfo contactInfo,
                    CustomerCategory category, CorporateContractInfo corporateContractInfo) {
        this.id = Objects.requireNonNull(id, "ID は null にできません");
        this.name = Objects.requireNonNull(name, "名前は null にできません");
        this.contactInfo = Objects.requireNonNull(contactInfo, "連絡先は null にできません");
        this.category = category;
        this.corporateContractInfo = corporateContractInfo;
        domainEvents.add(new ShipperRegisteredEvent(id, category));
    }

    public static Shipper registerIndividual(ShipperId id, ShipperName name, ContactInfo contactInfo) {
        if (id == null) throw new IllegalArgumentException("ID は null にできません");
        if (name == null) throw new IllegalArgumentException("名前は null にできません");
        if (contactInfo == null) throw new IllegalArgumentException("連絡先は null にできません");
        return new Shipper(id, name, contactInfo, CustomerCategory.INDIVIDUAL, null);
    }

    public static Shipper registerCorporate(ShipperId id, ShipperName name, ContactInfo contactInfo,
                                            CorporateContractInfo corporateContractInfo) {
        if (id == null) throw new IllegalArgumentException("ID は null にできません");
        if (name == null) throw new IllegalArgumentException("名前は null にできません");
        if (contactInfo == null) throw new IllegalArgumentException("連絡先は null にできません");
        if (corporateContractInfo == null) throw new IllegalArgumentException("法人契約情報は null にできません");
        return new Shipper(id, name, contactInfo, CustomerCategory.CORPORATE, corporateContractInfo);
    }

    public ShipperId getId() {
        return id;
    }

    public ShipperName getName() {
        return name;
    }

    public ContactInfo getContactInfo() {
        return contactInfo;
    }

    public CustomerCategory getCategory() {
        return category;
    }

    public CorporateContractInfo getCorporateContractInfo() {
        return corporateContractInfo;
    }

    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
