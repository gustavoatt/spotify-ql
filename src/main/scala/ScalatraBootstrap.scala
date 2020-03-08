import dev.gustavoatt.spotifyql._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    val spotifyClientId     = sys.env.get("SPOTIFY_CLIENT_ID")
    val spotifyClientSecret = sys.env.get("SPOTIFY_SECRET_ID")

    if (spotifyClientId.isEmpty || spotifyClientSecret.isEmpty) {
      throw new IllegalArgumentException("$SPOTIFY_CLIENT_ID and %SPOTIFY_SECRET_ID must be set")
    }
    context.mount(new SpotifyQl(spotifyClientId.get, spotifyClientSecret.get), "/*")
  }
}
