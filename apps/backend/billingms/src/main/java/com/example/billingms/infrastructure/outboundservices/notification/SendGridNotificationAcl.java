package com.example.billingms.infrastructure.outboundservices.notification;

import com.example.billingms.application.outboundservices.notification.NotificationAcl;
import com.example.billingms.config.NotificationProperties;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * billingms 用 SendGrid 実装（IT8 T3.2 / ADR-0018）。
 *
 * <p>billingms NotificationAcl の 3 メソッド（invoiceIssued / paymentReceived / overdue）を
 * SendGrid Dynamic Templates 経由で送信する。trackingms と同じ ADR-0018 方針：
 * 送信失敗時は WARN + counter のみ、業務フロー継続。</p>
 */
@Component
@ConditionalOnProperty(name = "notification.adapter", havingValue = "sendgrid")
public class SendGridNotificationAcl implements NotificationAcl {

    private static final Logger log = LoggerFactory.getLogger(SendGridNotificationAcl.class);

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
    public void notifyInvoiceIssued(String invoiceId,
                                    String shipperId,
                                    String invoiceNumber,
                                    LocalDate paymentDue,
                                    BigDecimal totalAmount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoiceId);
        data.put("shipperId", shipperId);
        data.put("invoiceNumber", invoiceNumber);
        data.put("paymentDue", paymentDue == null ? "" : paymentDue.toString());
        data.put("totalAmount", totalAmount == null ? "" : totalAmount.toPlainString());
        send(properties.sendgrid().templates().invoiceIssued(), data, "INVOICE_ISSUED");
    }

    @Override
    public void notifyPaymentReceived(String invoiceId,
                                      String shipperId,
                                      String paymentId,
                                      BigDecimal paidAmount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoiceId);
        data.put("shipperId", shipperId);
        data.put("paymentId", paymentId);
        data.put("paidAmount", paidAmount == null ? "" : paidAmount.toPlainString());
        send(properties.sendgrid().templates().paymentReceived(), data, "PAYMENT_RECEIVED");
    }

    @Override
    public void notifyOverdue(String invoiceId, String shipperId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoiceId);
        data.put("shipperId", shipperId);
        send(properties.sendgrid().templates().overdue(), data, "OVERDUE");
    }

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
