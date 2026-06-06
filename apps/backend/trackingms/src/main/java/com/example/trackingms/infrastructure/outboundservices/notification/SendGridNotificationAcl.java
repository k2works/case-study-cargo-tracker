package com.example.trackingms.infrastructure.outboundservices.notification;

import com.example.trackingms.application.outboundservices.notification.NotificationAcl;
import com.example.trackingms.config.NotificationProperties;
import com.example.trackingms.domain.model.TransportStatus;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SendGrid Dynamic Templates を利用する {@link NotificationAcl} 実装
 * （IT8 T3.1 / ADR-0018）。
 *
 * <p>{@code @ConditionalOnProperty(notification.adapter=sendgrid)} で有効化される。
 * デフォルト（{@code logging}）は {@code LoggingNotificationAcl} が使われる。</p>
 *
 * <p>送信失敗時の方針（ADR-0018）:</p>
 * <ul>
 *   <li>SendGrid client が IOException を投げた場合 → WARN ログ + counter increment、業務フローは止めない</li>
 *   <li>SendGrid が 4xx/5xx を返した場合 → 同上</li>
 * </ul>
 *
 * <p>監視メトリクス:</p>
 * <ul>
 *   <li>{@code notification.sent{status=success,channel=sendgrid}} - 送信成功件数</li>
 *   <li>{@code notification.sent{status=failure,channel=sendgrid,reason=...}} - 送信失敗件数</li>
 * </ul>
 *
 * <p>注: 本実装は通知のメイン宛先（荷主・荷受人）アドレスを引数に取らず、業務 ID（trackingNumber 等）から
 * 解決する Mapper / Repository をテンプレート側で参照する設計（Dynamic Template の dynamic_template_data に
 * 業務 ID を載せ、SendGrid 側で完成形のメール本文を生成）。実宛先メールアドレスの解決は IT9 で Shipper 情報統合時に追加予定。
 * 当面は {@link NotificationProperties.SendGrid#fromEmail()} 宛てに送信して動作確認のみ行う（本番テスト用）。</p>
 */
@Component
@ConditionalOnProperty(name = "notification.adapter", havingValue = "sendgrid")
public class SendGridNotificationAcl implements NotificationAcl {

    private static final Logger log = LoggerFactory.getLogger(SendGridNotificationAcl.class);

    private static final String DATA_TRACKING_NUMBER = "trackingNumber";
    private static final String DATA_EXCEPTION_ID = "exceptionId";

    private final SendGrid sendGrid;
    private final NotificationProperties properties;
    private final Counter successCounter;
    private final Counter failureCounter;

    public SendGridNotificationAcl(SendGrid sendGrid,
                                   NotificationProperties properties,
                                   MeterRegistry registry) {
        this.sendGrid = sendGrid;
        this.properties = properties;
        this.successCounter = Counter.builder("notification.sent")
                .tag("status", "success")
                .tag("channel", "sendgrid")
                .description("SendGrid 経由で通知メールが送信成功した件数")
                .register(registry);
        this.failureCounter = Counter.builder("notification.sent")
                .tag("status", "failure")
                .tag("channel", "sendgrid")
                .description("SendGrid 通知メール送信失敗件数")
                .register(registry);
    }

    @Override
    public void notifyTrackingIssued(String trackingNumber, String bookingId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_TRACKING_NUMBER, trackingNumber);
        data.put("bookingId", bookingId);
        send(properties.sendgrid().templates().trackingIssued(), data, "TRACKING_ISSUED");
    }

    @Override
    public void notifyStatusChanged(String trackingNumber,
                                    TransportStatus fromStatus,
                                    TransportStatus toStatus,
                                    String unlocode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_TRACKING_NUMBER, trackingNumber);
        data.put("fromStatus", fromStatus == null ? "" : fromStatus.name());
        data.put("toStatus", toStatus.name());
        data.put("unlocode", unlocode == null ? "" : unlocode);
        send(properties.sendgrid().templates().statusChanged(), data, "STATUS_CHANGED");
    }

    @Override
    public void notifyMisrouted(String trackingNumber, String unlocode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_TRACKING_NUMBER, trackingNumber);
        data.put("unlocode", unlocode == null ? "" : unlocode);
        send(properties.sendgrid().templates().misrouted(), data, "MISROUTED");
    }

    @Override
    public void notifyExceptionRegistered(String trackingNumber,
                                          String exceptionId,
                                          String exceptionType,
                                          String occurredUnlocode,
                                          String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_TRACKING_NUMBER, trackingNumber);
        data.put(DATA_EXCEPTION_ID, exceptionId);
        data.put("exceptionType", exceptionType);
        data.put("occurredUnlocode", occurredUnlocode == null ? "" : occurredUnlocode);
        data.put("description", description == null ? "" : description);
        send(properties.sendgrid().templates().exceptionRegistered(), data, "EXCEPTION_REGISTERED");
    }

    @Override
    public void notifyExceptionResolved(String trackingNumber,
                                        String exceptionId,
                                        String resolution) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_TRACKING_NUMBER, trackingNumber);
        data.put(DATA_EXCEPTION_ID, exceptionId);
        data.put("resolution", resolution == null ? "" : resolution);
        send(properties.sendgrid().templates().exceptionResolved(), data, "EXCEPTION_RESOLVED");
    }

    @Override
    public void notifyExceptionEscalation(String trackingNumber,
                                          String exceptionId,
                                          String exceptionType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_TRACKING_NUMBER, trackingNumber);
        data.put(DATA_EXCEPTION_ID, exceptionId);
        data.put("exceptionType", exceptionType);
        send(properties.sendgrid().templates().exceptionEscalation(), data, "EXCEPTION_ESCALATION");
    }

    /**
     * SendGrid Dynamic Template にデータを載せて送信。失敗時は WARN ログ + counter で監視可能に。
     */
    private void send(String templateId, Map<String, Object> dynamicData, String eventName) {
        if (templateId == null || templateId.isBlank()) {
            log.warn("[NOTIFY:SENDGRID:{}] templateId 未設定のためスキップ", eventName);
            failureCounter.increment();
            return;
        }
        try {
            Mail mail = new Mail();
            Email from = new Email(
                    properties.sendgrid().fromEmail(),
                    properties.sendgrid().fromName());
            mail.setFrom(from);
            mail.setTemplateId(templateId);

            Personalization p = new Personalization();
            // 当面は from 宛てに送信（本番テスト用、IT9 で Shipper 情報統合）
            p.addTo(new Email(properties.sendgrid().fromEmail()));
            dynamicData.forEach(p::addDynamicTemplateData);
            mail.addPersonalization(p);

            Request req = new Request();
            req.setMethod(Method.POST);
            req.setEndpoint("mail/send");
            req.setBody(mail.build());
            Response res = sendGrid.api(req);

            if (res.getStatusCode() >= 200 && res.getStatusCode() < 300) {
                successCounter.increment();
                log.info("[NOTIFY:SENDGRID:{}] sent template={} status={}",
                        eventName, templateId, res.getStatusCode());
            } else {
                failureCounter.increment();
                log.warn("[NOTIFY:SENDGRID:{}] failed template={} status={} body={}",
                        eventName, templateId, res.getStatusCode(), res.getBody());
            }
        } catch (IOException e) {
            failureCounter.increment();
            log.warn("[NOTIFY:SENDGRID:{}] IOException template={}: {}",
                    eventName, templateId, e.getMessage());
        }
    }
}
