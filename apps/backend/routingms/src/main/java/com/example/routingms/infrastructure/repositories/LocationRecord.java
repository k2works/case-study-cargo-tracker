package com.example.routingms.infrastructure.repositories;

/** location テーブルの 1 行。 */
public class LocationRecord {

    private String unlocode;
    private String name;

    public String getUnlocode() {
        return unlocode;
    }

    public void setUnlocode(String unlocode) {
        this.unlocode = unlocode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
