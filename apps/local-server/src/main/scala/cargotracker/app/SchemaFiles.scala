package cargotracker.app

import java.nio.file.{Files, Paths}
import sangria.renderer.SchemaRenderer

/** フロントエンドの型生成の入力になる、GraphQL スキーマの固定ファイル（F3）。 Query と Mutation は別のエンドポイント・別のスキーマなので、別のファイルに書き出す。
  */
object SchemaFiles:

  def render(): Map[String, String] =
    import scala.concurrent.ExecutionContext.Implicits.global
    // スキーマの形だけが要る。実際の問い合わせ先（DB・ユースケース）は使わないので、null を渡す。
    val query = new cargotracker.booking.queryadapter.BookingGraphQL(null).schema
    val mutation = new cargotracker.booking.commandadapter.BookingMutationGraphQL(null, null).schema
    // Sangria のスキーマ出力は、GraphQL の標準にない `Long` の宣言を含まない。型生成のために宣言を補う。
    def withScalars(text: String): String =
      val declared = List("Long")
        .filter(name => text.contains(s": $name") || text.contains(s"$name!"))
        .map(n => s"scalar $n\n\n")
      declared.mkString + text + "\n"
    Map(
      "query.graphql" -> withScalars(SchemaRenderer.renderSchema(query)),
      "command.graphql" -> withScalars(SchemaRenderer.renderSchema(mutation))
    )

  /** 書き出し直す：`sbt "local-server/runMain cargotracker.app.SchemaFiles"` */
  def main(args: Array[String]): Unit =
    val dir = Paths.get("apps/web/schema")
    Files.createDirectories(dir)
    render().foreach((name, text) => Files.writeString(dir.resolve(name), text))
