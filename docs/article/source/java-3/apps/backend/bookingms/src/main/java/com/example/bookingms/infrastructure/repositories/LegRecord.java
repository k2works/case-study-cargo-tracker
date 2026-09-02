package com.example.bookingms.infrastructure.repositories;

import java.time.Instant;

/**
 * {@code leg} テーブルの 1 行（US09）。
 *
 * <p>地点は名称まで取る。UN/LOCODE だけを返すと、画面が 5 文字のコードから地点名を
 * 引き直すことになり、その対応表がフロントとサーバの 2 箇所に増える。
 */
public class LegRecord {

    private Long id;
    private Long cargoId;
    private String voyageNumber;
    private String loadLocationUnlocode;
    private String loadLocationName;
    private String unloadLocationUnlocode;
    private String unloadLocationName;
    private Instant loadTime;
    private Instant unloadTime;
    private Integer seqNumber;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCargoId() {
        return cargoId;
    }

    public void setCargoId(Long cargoId) {
        this.cargoId = cargoId;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getLoadLocationUnlocode() {
        return loadLocationUnlocode;
    }

    public void setLoadLocationUnlocode(String loadLocationUnlocode) {
        this.loadLocationUnlocode = loadLocationUnlocode;
    }

    public String getLoadLocationName() {
        return loadLocationName;
    }

    public void setLoadLocationName(String loadLocationName) {
        this.loadLocationName = loadLocationName;
    }

    public String getUnloadLocationUnlocode() {
        return unloadLocationUnlocode;
    }

    public void setUnloadLocationUnlocode(String unloadLocationUnlocode) {
        this.unloadLocationUnlocode = unloadLocationUnlocode;
    }

    public String getUnloadLocationName() {
        return unloadLocationName;
    }

    public void setUnloadLocationName(String unloadLocationName) {
        this.unloadLocationName = unloadLocationName;
    }

    public Instant getLoadTime() {
        return loadTime;
    }

    public void setLoadTime(Instant loadTime) {
        this.loadTime = loadTime;
    }

    public Instant getUnloadTime() {
        return unloadTime;
    }

    public void setUnloadTime(Instant unloadTime) {
        this.unloadTime = unloadTime;
    }

    public Integer getSeqNumber() {
        return seqNumber;
    }

    public void setSeqNumber(Integer seqNumber) {
        this.seqNumber = seqNumber;
    }
}
