package com.example.bookingms.infrastructure.repositories;

import java.time.LocalDateTime;

/**
 * leg テーブルの永続化レコード（MyBatis 用 POJO）
 */
public class LegRecord {

    private Long id;
    private Long cargoId;
    private String voyageNumber;
    private String loadLocationUnlocode;
    private String unloadLocationUnlocode;
    private LocalDateTime loadTime;
    private LocalDateTime unloadTime;
    private Integer seqNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getLoadLocationUnlocode() { return loadLocationUnlocode; }
    public void setLoadLocationUnlocode(String loadLocationUnlocode) { this.loadLocationUnlocode = loadLocationUnlocode; }

    public String getUnloadLocationUnlocode() { return unloadLocationUnlocode; }
    public void setUnloadLocationUnlocode(String unloadLocationUnlocode) { this.unloadLocationUnlocode = unloadLocationUnlocode; }

    public LocalDateTime getLoadTime() { return loadTime; }
    public void setLoadTime(LocalDateTime loadTime) { this.loadTime = loadTime; }

    public LocalDateTime getUnloadTime() { return unloadTime; }
    public void setUnloadTime(LocalDateTime unloadTime) { this.unloadTime = unloadTime; }

    public Integer getSeqNumber() { return seqNumber; }
    public void setSeqNumber(Integer seqNumber) { this.seqNumber = seqNumber; }
}
