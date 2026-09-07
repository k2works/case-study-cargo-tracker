package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindPublicTrackingQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.PublicTrackingView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公開追跡照会（S44 / US18）。<b>認証を要らない</b>。
 *
 * <p>荷主は予約のたびに口座を作らず、渡された番号だけで見る。Gateway の
 * {@code PUBLIC_PATHS} に {@code /api/v1/tracking/public/**} が入っている。</p>
 *
 * <p><b>認証が無いぶん、守りは番号の形と回数で行う。</b> 番号は推測しにくい形式
 * （[ADR-0011]）、総当たりは Gateway のレート制限（`429`）で止める。片方だけでは
 * 足りない——形式を直しても回数を絞らなければ総当たりは通る。</p>
 *
 * <p><b>見つからないときは 404 を返す。</b> 存在しない番号と権限の無い番号を
 * 区別しない（ui_design.md）。区別すると、総当たりで「実在するが自分のものでは
 * ない番号」を選り分けられる。</p>
 */
@RestController
@RequestMapping("/api/v1/tracking/public")
public class PublicTrackingController {

    private final QueryDispatcher queries;

    public PublicTrackingController(QueryDispatcher queries) {
        this.queries = queries;
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<PublicTrackingView> find(@PathVariable String trackingNumber) {
        PublicTrackingView view = queries.query(
                new FindPublicTrackingQuery(trackingNumber), PublicTrackingView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }
}
