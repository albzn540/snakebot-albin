package se.cygni.snake;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketSession;
import se.cygni.snake.api.event.*;
import se.cygni.snake.api.exception.InvalidPlayerName;
import se.cygni.snake.api.model.*;
import se.cygni.snake.api.response.PlayerRegistered;
import se.cygni.snake.api.util.GameSettingsUtils;
import se.cygni.snake.client.AnsiPrinter;
import se.cygni.snake.client.BaseSnakeClient;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SimpleSnakePlayer extends BaseSnakeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSnakePlayer.class);

    // Set to false if you want to start the game from a GUI
    private static final boolean AUTO_START_GAME = true;

    // Personalise your game ...
    private static final String SERVER_NAME = "snake.cygni.se";
    private static  final int SERVER_PORT = 80;

    private static final GameMode GAME_MODE = GameMode.TRAINING;
    private static final String SNAKE_NAME = "Albins morsas son Albin";

    // Set to false if you don't want the game world printed every game tick.
    private static final boolean ANSI_PRINTER_ACTIVE = false;
    private AnsiPrinter ansiPrinter = new AnsiPrinter(ANSI_PRINTER_ACTIVE, true);

    //-------------------------------------- Own Stuff ----------------------------------------------//

    //These you can change:
    private int predictSteps = 20;

    //These you can not!
    private SnakeDirection lastDirection;
    private BetterMap betterMap;
    private String url;
    private ArrayList<Long> timers = new ArrayList<>();
    private int turns = 0;

    class WrapInt {
        public int value;
        public WrapInt() {
            value = 0;
        }
        public void increment() {
            value++;
        }
        public void reset(){
            value = 0;
        }
    }

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
        ansiPrinter.printMap(mapUpdateEvent);
        long startTimer = System.currentTimeMillis();

        // MapUtil contains lot's of useful methods for querying the map!
        MapUtil mapUtil = new MapUtil(mapUpdateEvent.getMap(), getPlayerId());
        betterMap = new BetterMap(mapUpdateEvent.getMap(), getPlayerId());
        betterMap.maxPredictSteps = predictSteps;

        // Let's see in which directions I can move
        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = betterMap.availableMoves(mapUtil.getMyPosition());

        List<Integer> sizeArray = new ArrayList<>();

        for (HashMap.Entry<SnakeDirection, MapCoordinate> entry : dirAndNewPos.entrySet()) {

            WrapInt sizeInt = new WrapInt();

            //Array with first childs
            List<PathElement> childs = new ArrayList<>();

            // each one on new thread?
            childs.add(new PathElement(
                    entry.getKey(),
                    entry.getValue(),
                    betterMap,
                    0,
                    sizeInt));

            sizeArray.add(sizeInt.value);
            sizeInt.reset();
        }

        int biggestYet = sizeArray.get(0);
        int index = 0;
        int biggestIndex = index;
        while( index < sizeArray.size()) {
            if(sizeArray.get(index) > biggestYet) {
                biggestYet = sizeArray.get(index);
                biggestIndex = index;
            }
            index++;
        }

        SnakeDirection chosenDirection = lastDirection;

        int currentIndex = 0;
        for (HashMap.Entry<SnakeDirection, MapCoordinate> entry : dirAndNewPos.entrySet()) {

            if(biggestIndex == currentIndex) {
                chosenDirection = entry.getKey();
            }

            currentIndex++;
        }

        MaximalTriangle tri = new MaximalTriangle();
        Rectangle rectangle = tri.maximalTriangle(betterMap);

        //System.out.println("Biggest rectangle at (" + rectangle.x + ", " + rectangle.y + ")" +
        //        "     With an area of: " + rectangle.area);

        long stopTimer = System.currentTimeMillis();
        timers.add(stopTimer-startTimer);
        //System.out.println("Chosen dir: " + chosenDirection + "          Walkdist: " + biggestYet);
        //System.out.println("Response time: " + (stopTimer-startTimer));

        registerMove(mapUpdateEvent.getGameTick(), chosenDirection);
        turns++;
        lastDirection = chosenDirection;
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

        LOGGER.info("The game is over and can be viewed at: {}", url);

        //calc average time
        long tot = 0;
        for (Long time : timers) {
            tot += time;
        }
        long average = tot / timers.size();
        LOGGER.info("Average response time: " + average);
        System.out.println("Number of turns " + turns);
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
