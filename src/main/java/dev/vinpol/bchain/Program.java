package dev.vinpol.bchain;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.core.util.Header;
import io.javalin.http.HttpCode;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class Program {

    private static final Logger logger = getLogger(Program.class);

    private static ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) throws Throwable {
        var env = dotEnv();

        SpotifyApi spotifyApi = getSpotifyApi(env);
        runInBackground(Duration.ofMinutes(30), () -> {
            logger.info("Fetching new spotify authentication token...");
            initSpotifyApi(spotifyApi);
        });

        DataSource dataSource = getDataSource(env);
        Jdbi jdbi = getJdbi(dataSource);
        Flyway flyway = flyway(dataSource);

        flyway.migrate();

        Javalin app = Javalin.create(config -> {
            config.enableCorsForAllOrigins();
        });

        app.get("/wedding/search", ctx -> {
            String query = ctx.queryParam("query");
            logger.debug("query: {}", query);

            try {
                SearchTracksRequest request = spotifyApi.searchTracks(query)
                        .limit(5)
                        .build();

                Paging<Track> tracksPage = request.execute();

                var response = Arrays.stream(tracksPage.getItems())
                        .map(t -> new TrackResponse(t.getId(), t.getArtists()[0].getName(), t.getName(), t.getAlbum().getImages()[2].getUrl()))
                        .toList();

                ctx.json(response);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });


        app.post("/wedding/register", ctx -> {
            try {
                String nameAttribute = ctx.formParam("name");
                int amountOfPeople = Integer.parseInt(ctx.formParam("people"));
                String food = String.join(",", ctx.formParams("food[]"));
                String spotifyId = ctx.formParam("spotify-id");
                String trackSuggestion = ctx.formParam("track-suggestion");
                String other = ctx.formParam("other");

                Registration newRegistration = new Registration(
                        nameAttribute,
                        amountOfPeople,
                        food,
                        spotifyId,
                        trackSuggestion,
                        other
                );

                jdbi.useHandle(handle -> {
                    handle.createUpdate("insert into wedding_registration(name, amount, food, track_suggestion, spotify_id, other) values(:name, :amount, :food, :track_suggestion, :spotify_id, :other);")
                            .bind("name", newRegistration.name())
                            .bind("amount", newRegistration.amountOfPeople())
                            .bind("food", newRegistration.food())
                            .bind("track_suggestion", newRegistration.trackSuggestion())
                            .bind("spotify_id", newRegistration.spotifyId())
                            .bind("other", newRegistration.other())
                            .execute();

                });
            } catch (Throwable t) {
                logger.error("Something went wrong with storing the registration", t);
                ctx.status(HttpCode.BAD_REQUEST);
            }
        });

        app.start();
    }

    private static void runInBackground(Duration duration, Runnable r) {
        background.scheduleAtFixedRate(r, 0, duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Jdbi getJdbi(DataSource dataSource) {
        return Jdbi.create(dataSource);
    }

    private static Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway
                .configure()
                .dataSource(dataSource)
                .load();

        return flyway;
    }

    private static DataSource getDataSource(Dotenv env) {
        String connectionString = env.get("DB_CONN");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);

        return new HikariDataSource(config);
    }

    private static Dotenv dotEnv() {
        return Dotenv.load();
    }

    private static SpotifyApi getSpotifyApi(Dotenv env) throws Throwable {
        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(env.get("SPOTIFY_ID"))
                .setClientSecret(env.get("SPOTIFY_SECRET"))
                .build();

        //  initSpotifyApi(spotifyApi);
        return spotifyApi;
    }

    private static void initSpotifyApi(final SpotifyApi api) {
        try {

            ClientCredentialsRequest credentials = api.clientCredentials()
                    .build();

            ClientCredentials clientCredentials = credentials.execute();
            api.setAccessToken(clientCredentials.getAccessToken());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

