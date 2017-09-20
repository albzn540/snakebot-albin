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
    private int predictSteps = 10;

    //These you can not!
    private SnakeDirection lastDirection;

    class WrapInt {
        public int value;
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

        // MapUtil contains lot's of useful methods for querying the map!
        MapUtil mapUtil = new MapUtil(mapUpdateEvent.getMap(), getPlayerId());



        //For use in PathElement tree
        MapCoordinate myPos = mapUtil.getMyPosition();                          //My snakes head MapCoorinate
        MapCoordinate myNewPos = myPos.translateBy(0, 0);           //My new pos based on direction
        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = new HashMap<>();    //HashMap with available moves and the new pos after that

        // Let's see in which directions I can move
        for (SnakeDirection direction : SnakeDirection.values()) {
            try {
                switch (direction) {
                    case DOWN:
                        myNewPos = myPos.translateBy(0, 1);
                        break;
                    case UP:
                        myNewPos = myPos.translateBy(0, -1);
                        break;
                    case LEFT:
                        myNewPos = myPos.translateBy(-1, 0);
                        break;
                    case RIGHT:
                        myNewPos = myPos.translateBy(1, 0);
                }

                if(mapUtil.isTileAvailableForMovementTo(myNewPos)) {
                    dirAndNewPos.put(direction, myNewPos);
                }

            } catch (Exception e) {
                LOGGER.error("Something went horribly wrong when " +
                        "calculating the new position");
            }
        }

        List<Integer> sizeArray = new ArrayList<>();

        for (HashMap.Entry<SnakeDirection, MapCoordinate> entry : dirAndNewPos.entrySet()) {

            WrapInt sizeInt = new WrapInt();

            //Array with first childs
            List<PathElement> childs = new ArrayList<>();

            // each one on new thread?
            childs.add(new PathElement(
                    entry.getKey(),
                    entry.getValue(),
                    mapUtil.listCoordinatesContainingObstacle(),
                    otherSnakeHeads,
                    predictSteps,
                    sizeInt,
                    mapUtil));

            sizeArray.add(sizeInt.value);
            System.out.println("SizePointer: " + sizeInt.value);
        }

        int biggestYet = sizeArray.get(0);
        int index = 0;
        while( index < sizeArray.size()) {
            if(sizeArray.get(index) > biggestYet) {
                biggestYet = sizeArray.get(index);
            }
            index++;
        }

        SnakeDirection chosenDirection = SnakeDirection.UP;

        System.out.println("Done Calculating, biggest value is: " + biggestYet);
        System.out.println("With the index of: " + index);

        int currentIndex = 0;
        for (HashMap.Entry<SnakeDirection, MapCoordinate> entry : dirAndNewPos.entrySet()) {
            if(index == currentIndex) {
                chosenDirection = entry.getKey();
                break;
            }
            currentIndex++;
        }

        registerMove(mapUpdateEvent.getGameTick(), chosenDirection);
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
    }

    @Override
    public void onGameLink(GameLinkEvent gameLinkEvent) {
        LOGGER.info("The game can be viewed at: {}", gameLinkEvent.getUrl());
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
