package com.example.bookingms.domain.projections;

import java.time.LocalDateTime;

/**
 * 確定旅程の輸送区間 Read Model（US11 / cargo_leg テーブル）。
 *
 * <p>経路確定（CargoRoutedEvent）で booking_id 単位に確定する。{@code legSeq} は 1 始まりの順序。</p>
 */
public class CargoLeg {

    private String bookingId;
    private int legSeq;
    private String voyageNumber;
    private String loadUnlocode;
    private String unloadUnlocode;
    private LocalDateTime loadAt;
    private LocalDateTime unloadAt;

    public CargoLeg() { /* MyBatis result mapping */ }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public int getLegSeq() { return legSeq; }
    public void setLegSeq(int legSeq) { this.legSeq = legSeq; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getLoadUnlocode() { return loadUnlocode; }
    public void setLoadUnlocode(String loadUnlocode) { this.loadUnlocode = loadUnlocode; }

    public String getUnloadUnlocode() { return unloadUnlocode; }
    public void setUnloadUnlocode(String unloadUnlocode) { this.unloadUnlocode = unloadUnlocode; }

    public LocalDateTime getLoadAt() { return loadAt; }
    public void setLoadAt(LocalDateTime loadAt) { this.loadAt = loadAt; }

    public LocalDateTime getUnloadAt() { return unloadAt; }
    public void setUnloadAt(LocalDateTime unloadAt) { this.unloadAt = unloadAt; }
}
