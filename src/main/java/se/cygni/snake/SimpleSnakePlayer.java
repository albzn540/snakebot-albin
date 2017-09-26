package se.cygni.snake;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketSession;
import se.cygni.snake.api.event.*;
import se.cygni.snake.api.exception.InvalidPlayerName;
import se.cygni.snake.api.model.GameMode;
import se.cygni.snake.api.model.GameSettings;
import se.cygni.snake.api.model.PlayerPoints;
import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.api.response.PlayerRegistered;
import se.cygni.snake.api.util.GameSettingsUtils;
import se.cygni.snake.client.AnsiPrinter;
import se.cygni.snake.client.BaseSnakeClient;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public class SimpleSnakePlayer extends BaseSnakeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSnakePlayer.class);

    // Set to false if you want to start the game from a GUI
    private static final boolean AUTO_START_GAME = true;

    // Personalise your game ...
    private static final String SERVER_NAME = "snake.cygni.se";
    private static  final int SERVER_PORT = 80;

    private static final GameMode GAME_MODE = GameMode.TRAINING;
    private static final String SNAKE_NAME = "Albin";

    // Set to false if you don't want the game world printed every game tick.
    private static final boolean ANSI_PRINTER_ACTIVE = false;
    private AnsiPrinter ansiPrinter = new AnsiPrinter(ANSI_PRINTER_ACTIVE, true);

    //-------------------------------------- Own Stuff ----------------------------------------------//

    //These you can change:
    private int predictSteps = 10;
    private int distToEnemiesDepth = 2;

    //These you can not!
    private SnakeDirection chosenDirection;
    private BetterMap betterMap;
    private String url;
    private ArrayList<Long> timers = new ArrayList<>();
    private int turns;


    public static void main(String[] args) {
        SimpleSnakePlayer simpleSnakePlayer = new SimpleSnakePlayer();

        try {
            ListenableFuture<WebSocketSession> connect = simpleSnakePlayer.connect();
            connect.get();
        } catch (Exception e) {
            LOGGER.error("Failed to connect to server", e);
            System.exit(1);
        }

        startTheSnake(simpleSnakePlayer);
    }

    /**
     * The Snake client will continue to run ...
     * : in TRAINING mode, until the single game ends.
     * : in TOURNAMENT mode, until the server tells us its all over.
     */
    private static void startTheSnake(final SimpleSnakePlayer simpleSnakePlayer) {
        Runnable task = () -> {
            do {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (simpleSnakePlayer.isPlaying());

            LOGGER.info("Shutting down");
        };

        Thread thread = new Thread(task);
        thread.start();
    }

    @Override
    public void onMapUpdate(MapUpdateEvent mapUpdateEvent) {

        System.out.println("------------------- Turn " + turns + " -----------------------");
        long startTimer = System.currentTimeMillis();

        ansiPrinter.printMap(mapUpdateEvent);

        // MapUtil contains lot's of useful methods for querying the map!
        MapUtil mapUtil = new MapUtil(mapUpdateEvent.getMap(), getPlayerId());
        BetterMap betterMap = new BetterMap(mapUpdateEvent.getMap(), getPlayerId());
        betterMap.maxPredictSteps = predictSteps;

        // Let's see in which directions I can move
        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = betterMap.availableMoves(mapUtil.getMyPosition());
        // Array with the path options
        ArrayList<PathElement> pathOptions = new ArrayList<>();

        int id = 0; //assign an id to every option
        for (HashMap.Entry<SnakeDirection, MapCoordinate> entry : dirAndNewPos.entrySet()) {
            betterMap = new BetterMap(mapUpdateEvent.getMap(), getPlayerId());
            betterMap.maxPredictSteps = predictSteps;

            // each one on new thread? YESS, I think the bruteforce method WITH duplications is better
            pathOptions.add(new PathElement(
                    entry.getKey(),
                    entry.getValue(),
                    betterMap,
                    id));
            id++;
        }

        //Print PathOptions
        int nr = 0;
        for(PathElement option : pathOptions) {
            System.out.print(option.id + ": " + option.direction);
            //System.out.println(", new Pos: " + option.currentCoordinate.toString());
            System.out.print(", Sub-options: " + option.pathOptions.size());
            System.out.print(", nodes: " + option.nodes);

            int sum = 0;
            for (Integer tile : option.enemyTiles) {
                sum += tile;
            }
            sum /= option.enemyTiles.size();
            System.out.print(", Average Enemy tiles: " + sum);

            for (Integer tile : option.ownTiles) {
                sum += tile;
            }
            sum /= option.ownTiles.size();
            System.out.print(", Average Own tiles: " + sum);

            System.out.print("\n");
            nr++;
        }

        // Register action here!
        registerMove(mapUpdateEvent.getGameTick(), chosenDirection);
        System.out.print("Chosen direction: " + chosenDirection);

        // ----------- This happens after response ----------- //
        long stopTimer = System.currentTimeMillis();
        long time = stopTimer-startTimer;
        timers.add(time);
        turns++;

        System.out.print("            ");
        System.out.println("Response time: " + time +"\n");
    }


    @Override
    public void onInvalidPlayerName(InvalidPlayerName invalidPlayerName) {
        LOGGER.debug("InvalidPlayerNameEvent: " + invalidPlayerName);
    }

    @Override
    public void onSnakeDead(SnakeDeadEvent snakeDeadEvent) {
        LOGGER.info("A snake {} died by {}",
                snakeDeadEvent.getPlayerId(),
                snakeDeadEvent.getDeathReason());
    }

    @Override
    public void onGameResult(GameResultEvent gameResultEvent) {

        LOGGER.info("Game result:");
        gameResultEvent.getPlayerRanks().forEach(playerRank -> LOGGER.info(playerRank.toString()));

        System.out.println("");
        LOGGER.info("The game is over and can be viewed at: {}", url);

        //calc average time
        long tot = 0;
        for (Long time : timers) {
            tot += time;
        }
        long average = tot / timers.size();
        LOGGER.info("Average response time: " + average);
        LOGGER.info("You survived " + turns + " number of turns.");
        System.out.println("");
    }

    @Override
    public void onGameEnded(GameEndedEvent gameEndedEvent) {
        LOGGER.debug("GameEndedEvent: " + gameEndedEvent);
    }

    @Override
    public void onGameStarting(GameStartingEvent gameStartingEvent) {
        LOGGER.debug("GameStartingEvent: " + gameStartingEvent);
    }

    @Override
    public void onPlayerRegistered(PlayerRegistered playerRegistered) {
        LOGGER.info("PlayerRegistered: " + playerRegistered);

        if (AUTO_START_GAME) {
            startGame();
        }
    }

    @Override
    public void onTournamentEnded(TournamentEndedEvent tournamentEndedEvent) {
        LOGGER.info("Tournament has ended, winner playerId: {}", tournamentEndedEvent.getPlayerWinnerId());
        int c = 1;
        for (PlayerPoints pp : tournamentEndedEvent.getGameResult()) {
            LOGGER.info("{}. {} - {} points", c++, pp.getName(), pp.getPoints());
        }

        LOGGER.info("Watch the game at: {}", url);
    }

    @Override
    public void onGameLink(GameLinkEvent gameLinkEvent) {
        LOGGER.info("The game can be viewed at: {}", gameLinkEvent.getUrl());
        url = gameLinkEvent.getUrl();
    }

    @Override
    public void onSessionClosed() {
        LOGGER.info("Session closed");
    }

    @Override
    public void onConnected() {
        LOGGER.info("Connected, registering for training...");
        GameSettings gameSettings = GameSettingsUtils.trainingWorld();
        registerForGame(gameSettings);
    }

    @Override
    public String getName() {
        return SNAKE_NAME;
    }

    @Override
    public String getServerHost() {
        return SERVER_NAME;
    }

    @Override
    public int getServerPort() {
        return SERVER_PORT;
    }

    @Override
    public GameMode getGameMode() {
        return GAME_MODE;
    }
}