import { queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

export interface AttentionItemView {
  readonly itemId: string;
  readonly kind: string;
  readonly targetType: string;
  readonly targetId: string;
  readonly assignedRole: string;
  readonly reason: string;
  readonly occurredAt: string;
}

export function fetchAttentionItems(): Promise<Pending<{ items: AttentionItemView[] }>> {
  return queryClient('/booking/attention-items');
}
