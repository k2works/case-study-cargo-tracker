package com.example.handlingms.domain.model;

/**
 * 荷役の種別ごとに、作業場所を照らし合わせる相手（[ADR-023] 決定 1）。
 *
 * <p>港そのものではなく<strong>どこと照らすか</strong>を表す。実際の港は貨物によって違い、
 * 旅程の区間はいくつもある。ここで港を持たせると、種別が貨物を知ることになる。
 */
public enum ExpectedPort {
    /** 予約の出発港。受領はここで行われるはずである。 */
    ORIGIN,
    /** 旅程のいずれかの区間の積込港。 */
    ITINERARY_LOAD,
    /** 旅程のいずれかの区間の荷降港。 */
    ITINERARY_UNLOAD,
    /** 予約の目的港。引取はここで行われるはずである。 */
    DESTINATION
}
