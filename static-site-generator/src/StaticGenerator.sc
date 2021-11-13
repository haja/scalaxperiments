// no scalatags for scala 3.0 available yet
import $ivy.`com.lihaoyi:scalatags_2.13:0.9.1`, scalatags.Text.all._
import $ivy.`com.atlassian.commonmark:commonmark:0.13.1`
import $ivy.`dev.zio::zio:2.0.0-M4`, zio._

import java.io.IOException

case class WorkingPaths(source: os.Path, target: os.Path)

object MdTransformer:
  val parser = org.commonmark.parser.Parser.builder().build()
  val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()

  def transform(source: String): scala.util.Try[String] =
    for {
      doc <- scala.util.Try(parser.parse(source))
      rendered <- scala.util.Try(renderer.render(doc))
    } yield rendered


case class Post(id: Int, name: String, source: os.Path):
  private val target: ZIO[Has[WorkingPaths], Nothing, os.Path] =
    ZIO.serviceWith[WorkingPaths](service =>
        ZIO.succeed(service.target / "posts" / s"$id-${name.replace(" ", "_")}.html")
        )

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

  val asHeading: ZIO[Has[WorkingPaths], Nothing, Frag] =
    for {
      targetHref <- target
      frag <- ZIO.succeed(
        h2(
          a(href := targetHref.toString)(
            name
          )
        )
      )
    } yield frag

  val writePost: ZIO[Has[WorkingPaths], IOException, Unit] =
    for {
      h <- asHtml
      outPath <- target
      _ <- IO.attemptBlockingIO(os.write(outPath, h.render))
    } yield ()

case class Index(posts: Seq[Post]):
  private val asHtml: ZIO[Has[WorkingPaths], Nothing, Frag] =
    for {
      postHeadings <- ZIO.foreach(posts)(_.asHeading)
    } yield
    html(
      head(),
      body(
        h1("Posts"),
        postHeadings
    ))

  val write: ZIO[Has[WorkingPaths], Throwable, Unit] =
    ZIO.serviceWith[WorkingPaths](path =>
        for {
          h <- asHtml
          _ <- IO.attemptBlockingIO {
            os.write(path.target / "index.html", h.render)
          }
        } yield ()
      )

val loadPosts: ZIO[Has[WorkingPaths], IOException, Seq[Post]] =
  ZIO.serviceWith[WorkingPaths](path =>
    for {
      posts <- IO.attemptBlockingIO {
        os.list(path.source)
          .filter(_.toString.endsWith(".md"))
          .map (p => {
            val s"$id-$name.md" = p.last
            Post(id.toInt, name, p)
          })
            .sortBy(_._1)
      }
    } yield posts
  )


val recreateOut: ZIO[Has[WorkingPaths], IOException, Unit] =
  ZIO.serviceWith[WorkingPaths](p =>
      val path = p.target
      for {
        _ <- ZIO.attemptBlockingIO {
          os.remove.all(path)
          os.makeDir(path)
          os.makeDir(path / "posts")
        }
      } yield ()
  )

val program =
  for {
    _ <- Console.printLine("cleaning target")
    _ <- recreateOut
    _ <- Console.printLine("loading posts")
    posts <- loadPosts
    _ <- Console.printLine(s"found ${posts.length} posts")
    index = Index(posts)
    _ <- index.write
    _ <- Console.printLine("written index")
    _ <- ZIO.foreachPar(posts)(_.writePost)
    _ <- Console.printLine("written posts")
  } yield ()

def run(
  source: String = (os.pwd / "posts").toString,
  target: String = (os.pwd / "out").toString
  ) =
    val paths = WorkingPaths(os.Path(source), os.Path(target))
    Runtime.default.unsafeRun(program.provideSome(r => Has(paths) ++ r))

