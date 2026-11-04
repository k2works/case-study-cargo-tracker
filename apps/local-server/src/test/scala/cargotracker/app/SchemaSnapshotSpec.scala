package cargotracker.app

import java.nio.file.{Files, Path, Paths}
import org.scalatest.funsuite.AnyFunSuite

/** F3：フロントエンドが使う GraphQL スキーマの固定ファイルは、サーバーの実際のスキーマと一致する。 一致しなければ失敗する（スキーマを変えたら、`SchemaFiles`
  * で書き出し直して、フロントエンドの型を再生成する）。
  */
class SchemaSnapshotSpec extends AnyFunSuite:
  private val root: Path = Paths.get(sys.props.getOrElse("cargotracker.root", "."))

  SchemaFiles.render().foreach { (name, expected) =>
    test(s"F3: apps/web/schema/$name は、サーバーのスキーマと一致する") {
      val file = root.resolve("apps/web/schema").resolve(name)
      assert(Files.exists(file), s"$file がありません。SchemaFiles を実行して書き出してください")
      assert(Files.readString(file) == expected, s"$name が古い。SchemaFiles を実行して書き出し直し、型を再生成してください")
    }
  }

  test("公開スキーマの固定（A7 の土台）: Query の項目は一覧どおりで、増えたら失敗する") {
    val query = SchemaFiles.render()("query.graphql")
    val fields = "(?m)^  (\\w+)\\(".r
      .findAllMatchIn(query.split("type Query").last.takeWhile(_ != '}').toString + "(")
      .map(_.group(1))
      .toList
    // 社内向けの Query（個人情報を含む）。公開照会（Bolt 8）は別スキーマで、ここには載せない
    assert(
      query.contains("shippers") && query.contains("bookings") && query.contains(
        "booking("
      ) && query.contains("shipper(")
    )
    assert(!query.contains("trackingNumber"), "公開照会の項目は、このスキーマに混ぜない")
  }
