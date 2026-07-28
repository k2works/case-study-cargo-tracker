# frozen_string_literal: true

module Shared
  module Public
    # 通知送信記録の公開 API（ADR-0002）。全 BC の通知ハンドラが利用する。
    # 送信を試行し notifications テーブルに pending→sent/failed で永続化する。
    class NotificationRecorder
      # 送信記録の公開ビュー。
      View = Data.define(:id, :event_type, :recipient_type, :recipient_address, :status, :sent_at)

      def record(notifiable_type:, notifiable_id:, event_type:, recipient_type:,
                 recipient_address:, subject: nil, body: nil)
        record = Infrastructure::NotificationRecord.create!(
          notifiable_type: notifiable_type, notifiable_id: notifiable_id, event_type: event_type,
          recipient_type: recipient_type, recipient_address: recipient_address.to_s,
          subject: subject, body: body, status: "pending"
        )

        if recipient_address.to_s.strip.empty?
          record.update!(status: "failed")
        else
          # 実際の送信（メール等）は将来のアダプタに委譲。IT4 は記録のみで送信成功とみなす。
          record.update!(status: "sent", sent_at: Time.current)
        end
        to_view(record)
      end

      def for(notifiable_type:, notifiable_id:)
        Infrastructure::NotificationRecord
          .where(notifiable_type: notifiable_type, notifiable_id: notifiable_id)
          .order(:created_at).map { |r| to_view(r) }
      end

      private

      def to_view(record)
        View.new(id: record.id, event_type: record.event_type, recipient_type: record.recipient_type,
                 recipient_address: record.recipient_address, status: record.status, sent_at: record.sent_at)
      end
    end
  end
end
