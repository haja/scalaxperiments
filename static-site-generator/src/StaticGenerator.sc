// no scalatags for scala 3.0 available yet
import $ivy.`com.lihaoyi:scalatags_2.13:0.9.1`, scalatags.Text.all._
import $ivy.`com.atlassian.commonmark:commonmark:0.13.1`
import $ivy.`dev.zio::zio:2.0.0-M4`, zio._

import java.io.IOException

interp.watch(os.pwd / "posts")

val targetBase = os.pwd / "out"

object MdTransformer:
  val parser = org.commonmark.parser.Parser.builder().build()
  val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()

  def transform(source: String): scala.util.Try[String] =
    for {
      doc <- scala.util.Try(parser.parse(source))
      rendered <- scala.util.Try(renderer.render(doc))
    } yield rendered


case class Post(id: Int, name: String, source: os.Path):
  def target: os.Path = targetBase / "posts" / s"$id-${name.replace(" ", "_")}.html"

  private val htmlContent: IO[IOException, String] =
    for {
      content <- ZIO.attemptBlockingIO(os.read(source))
      html <- ZIO.fromTry(MdTransformer.transform(content)).orDie
    } yield html

  val asHtml: IO[IOException, Frag] =
    for {
      content <- htmlContent
    } yield
    html(
      head(),
      body(
        h1(
          a(href := "../index.html")(
            name
          )),
      raw(content)
    )
  )

  val asHeading: UIO[Frag] =
    ZIO.succeed(
      h2(
        a(href := target.toString)(
          name
        )
      )
    )

  val writePost: Task[Unit] =
    for {
      h <- asHtml
      _ <- IO.attemptBlockingIO(os.write(target, h.render))
    } yield ()

case class Index(posts: Seq[Post]):
  private val asHtml: UIO[Frag] =
    for {
      postHeadings <- ZIO.foreach(posts)(_.asHeading)
    } yield
    html(
      head(),
      body(
        h1("Posts"),
        postHeadings
    ))

  val write: Task[Unit] =
    for {
      h <- asHtml
      _ <- IO.attemptBlockingIO {
        os.write(targetBase / "index.html", h.render)
      }
    } yield ()

val loadPosts: IO[IOException, Seq[Post]] =
  IO.attemptBlockingIO {
    os.list(os.pwd / "posts")
      .filter(_.toString.endsWith(".md"))
      .map (p => {
        val s"$id-$name.md" = p.last
        Post(id.toInt, name, p)
      })
        .sortBy(_._1)
  }


def recreateOut(targetBase: os.Path): IO[IOException, Unit] =
  ZIO.attemptBlockingIO {
    os.remove.all(targetBase)
    os.makeDir(targetBase)
    os.makeDir(targetBase / "posts")
  }


val program = for {
  _ <- Console.printLine("cleaning target")
  _ <- recreateOut(targetBase)
  _ <- Console.printLine("loading posts")
  posts <- loadPosts
  _ <- Console.printLine(s"found ${posts.length} posts")
  index = Index(posts)
  _ <- index.write
  _ <- Console.printLine("written index")
  _ <- ZIO.foreachPar(posts)(_.writePost)
  _ <- Console.printLine("written posts")
} yield ()

def run = Runtime.default.unsafeRun(program)

