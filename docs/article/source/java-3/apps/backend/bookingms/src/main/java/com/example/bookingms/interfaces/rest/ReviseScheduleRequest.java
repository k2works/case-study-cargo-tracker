package com.example.bookingms.interfaces.rest;

/**
 * 予約の日程の訂正（IT6 タスク 0.11）。
 *
 * <p><strong>日付は文字列で受ける。</strong>形式の誤りをフレームワークに任せると、
 * 何が悪いのかを利用者の言葉で伝えられない（[ADR-016] 決定 2 と同じ理由）。
 *
 * @param departureDate 出発希望日（`YYYY-MM-DD`。指定しないなら空）
 * @param arrivalDeadline 到着期限（`YYYY-MM-DD`。必須）
 */
public record ReviseScheduleRequest(String departureDate, String arrivalDeadline) {
}
