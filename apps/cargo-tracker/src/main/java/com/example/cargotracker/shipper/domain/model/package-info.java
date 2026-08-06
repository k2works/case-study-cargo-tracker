/**
 * 荷主コンテキストのドメインモデル。
 *
 * <p>{@code Shipper} 集約と、その構成要素である値オブジェクト（荷主コード・荷主名・
 * メールアドレス・電話番号・住所・荷主種別）を置く。用語は
 * docs/design/domain-model.md「2. Shipper Context」のユビキタス言語に従う。
 *
 * <p>値オブジェクトは生成時に不変条件を検証する。<strong>不正な値を持つオブジェクトを
 * 作れないようにするのが目的であり、検証を画面側に置かない。</strong> 画面の検証は
 * 利用者への案内であって、モデルの正しさの担保ではない。
 */
package com.example.cargotracker.shipper.domain.model;
