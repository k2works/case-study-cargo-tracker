import { EstimateValidationError } from './estimate-validation-error.js';
interface RouteCandidateProps {
  voyageNumber: string;
  transitPort?: string | null;
  transitDays: number;
  estimatedCost: number;
}

/**
 * ルート候補（値オブジェクト）。見積に紐づく輸送ルートの概算。
 * 現在はスタブ算出（重量ベース固定計算）。将来は外部ルーティングサービスに置換。
 */
export class RouteCandidate {
  private constructor(
    readonly voyageNumber: string,
    readonly transitPort: string | null,
    readonly transitDays: number,
    readonly estimatedCost: number,
  ) {}

  static of(props: RouteCandidateProps): RouteCandidate {
    if (!props.voyageNumber || props.voyageNumber.trim().length === 0) {
      throw new EstimateValidationError('航海番号は必須です');
    }
    if (props.transitDays <= 0) {
      throw new EstimateValidationError('所要日数は正の値が必要です');
    }
    if (props.estimatedCost <= 0) {
      throw new EstimateValidationError('概算料金は正の値が必要です');
    }
    return new RouteCandidate(
      props.voyageNumber,
      props.transitPort ?? null,
      props.transitDays,
      props.estimatedCost,
    );
  }
}
