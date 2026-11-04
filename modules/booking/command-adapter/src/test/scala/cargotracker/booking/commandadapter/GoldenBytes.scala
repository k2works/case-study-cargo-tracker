package cargotracker.booking.commandadapter

/** 固定した旧版のバイト列（`BookingRegistered_V1`）。**書き換えない**。 互換性を壊す変更をしたら、新しい版を足して旧版を読む変換（アップキャスト）を置く。
  * 2026-11-04 に、BookingEventSerializer の出力を 16 進で固定した。
  */
object GoldenBytes:
  private def hex(s: String): Array[Byte] = s.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray

  val bookingRegisteredV1: Array[Byte] =
    hex(
      "0a11426f6f6b696e675265676973746572656410011ad0010a1a303141525a33" +
        "4e44454b54535634525246465136394735464157108b88aad706188898e8d901" +
        "221a303141525a334e44454b545356345252464651363947354641582a09424b" +
        "2d303030303031321a303141525a334e44454b54535634525246465136394735" +
        "4641563a0a494e444956494455414c420747454e4552414c4a0ce7b2bee5af86" +
        "e6a99fe599a852053132302e355a03313030620538302e32356a02363070037a" +
        "054a5054594f8201054e4c52544d8a010a323032362d31322d303192010a3230" +
        "32372d30312d3135"
    )
