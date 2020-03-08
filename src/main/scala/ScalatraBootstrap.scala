import dev.gustavoatt.spotifyql._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    val spotifyClientId     = context.getInitParameter("dev.gustavoatt.spotify.clientId")
    val spotifyClientSecret = context.getInitParameter("dev.gustavoatt.spotify.clientSecret")
    context.mount(new SpotifyQl(spotifyClientId, spotifyClientSecret), "/*")
  }
}
