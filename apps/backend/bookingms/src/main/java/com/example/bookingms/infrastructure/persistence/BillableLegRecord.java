package com.example.bookingms.infrastructure.persistence;

/**
 * 料金算出のために引く旅程の 1 区間（[ADR-027] 決定 1 の改訂）。
 *
 * <p><strong>地域区分だけを運ぶ。</strong>係数は billingms が決める
 * （[ADR-028] 決定 6——料金の式は 1 か所にある）。
 */
public class BillableLegRecord {

    private String bookingId;

    private String loadRegion;

    private String unloadRegion;

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getLoadRegion() {
        return loadRegion;
    }

    public void setLoadRegion(String loadRegion) {
        this.loadRegion = loadRegion;
    }

    public String getUnloadRegion() {
        return unloadRegion;
    }

    public void setUnloadRegion(String unloadRegion) {
        this.unloadRegion = unloadRegion;
    }
}
