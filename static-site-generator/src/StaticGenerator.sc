// no scalatags for scala 3.0 available yet
import $ivy.`com.lihaoyi:scalatags_2.13:0.9.1`, scalatags.Text.all._
import $ivy.`com.atlassian.commonmark:commonmark:0.13.1`, scalatags.Text.all._

val targetBase = os.pwd / "out"
os.remove.all(targetBase)
os.makeDir(targetBase)
os.makeDir(targetBase / "posts")

val parser = org.commonmark.parser.Parser.builder().build()
val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()
def mdTransformer(source: String): String =
  val doc = parser.parse(source)
  renderer.render(doc)


case class Post(id: Int, name: String, source: os.Path):
  def target: os.Path = targetBase / "posts" / s"$id-${name.replace(" ", "_")}.html"
  def asHtml: String = mdTransformer(os.read(source))

interp.watch(os.pwd / "posts")

val posts = os.list(os.pwd / "posts")
  .filter(_.toString.endsWith(".md"))
  .map (p => {
    val s"$id-$name.md" = p.last
    Post(id.toInt, name, p)
  })
  .sortBy(_._1)


def toHeading(posts: Seq[Post]) = for {
  p@Post(_, name, _) <- posts
} yield h2(
  a(href := p.target.toString)(
    name
  )
)

val index = html(
  head(),
  body(
    h1(
        "Posts"
      ),
  toHeading(posts)
))


println(index.toString)
os.write(os.pwd / "out" / "index.html", index)

def writePost(post: Post) = html(
  head(),
  body(
    h1(
      a(href := "../index.html")(
      post.name
    )),
    raw(post.asHtml)
  )
)

for (p <- posts)
  os.write(p.target, writePost(p))



