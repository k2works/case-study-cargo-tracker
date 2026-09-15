package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.tracking.infrastructure.query.NoticeQueries;
import com.example.cargotracker.tracking.infrastructure.query.NoticeQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 荷主への知らせ（US37）。
 *
 * <p><b>入口を足したら、意図した相手以外が通る道を書き出す</b>（IT16 の教訓）。</p>
 *
 * <table>
 *   <caption>この入口で起きること</caption>
 *   <tr><th>誰が</th><th>何が起きるか</th><th>気づけるか</th></tr>
 *   <tr><td>荷主</td><td>自社の貨物の知らせだけが返る</td>
 *       <td>絞るのは SQL（荷主 ID はヘッダ）。他社の行は返らない</td></tr>
 *   <tr><td>追跡番号を知っている他社の荷主</td><td>返らない</td>
 *       <td>追跡番号では引かない。<b>荷主 ID で絞る</b>（§4）</td></tr>
 *   <tr><td>荷主以外のロール</td><td>403</td>
 *       <td>Gateway が断る。画面も<b>問い合わせにも行かない</b>（§5）</td></tr>
 *   <tr><td>紐付けの無い利用者</td><td>403</td>
 *       <td>「荷主 ID が無いなら全件」に倒さない</td></tr>
 * </table>
 *
 * <p><b>既読はサーバが持つ</b>（§3）。ブラウザに持つと、荷主が端末を使い分けた
 * とき同じ知らせが行く先々でもう一度出る。</p>
 */
@RestController
@RequestMapping("/api/v1/tracking/notices")
public class NoticeController {

    private final NoticeQueryHandler notices;

    public NoticeController(NoticeQueryHandler notices) {
        this.notices = notices;
    }

    /** 未読の知らせ（§1）。<b>自社のぶんだけ</b>。 */
    @GetMapping
    public ResponseEntity<NoticeQueries.NoticeListView> unread(
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId) {
        return ResponseEntity.ok(notices.findUnread(required(shipperId)));
    }

    /**
     * ここまで読んだ（§3）。
     *
     * <p><b>位置はサーバが返したものを送り返す。</b> 画面が自分で最大値を数えると、
     * 上限で切れたときに「出していない知らせまで既読」にしてしまう。</p>
     */
    @PostMapping("/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId,
            @RequestBody ReadRequest request) {
        notices.markRead(required(shipperId), request.sequenceNo());
        return ResponseEntity.noContent().build();
    }

    private static String required(String shipperId) {
        if (shipperId == null || shipperId.isBlank()) {
            // 紐付けが済んでいない荷主。全件を見せるより断るほうが害が小さい。
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "荷主の紐付けがありません。担当者にお問い合わせください");
        }
        return shipperId;
    }

    /** 既読の位置。 */
    public record ReadRequest(long sequenceNo) {
    }
}
