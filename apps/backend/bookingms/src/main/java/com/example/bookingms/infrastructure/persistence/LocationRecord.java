package com.example.bookingms.infrastructure.persistence;

/** location テーブルの 1 行。 */
public class LocationRecord {

    private String unlocode;
    private String name;
    private String countryCode;
    private String timeZone;

    /** 地域区分（[ADR-027] 決定 1 の改訂）。**距離の代わり**であり、料金の試算に効く。 */
    private String region;

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

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
