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

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    //Change max predictsteps in PathElement!

    //These you can not!
    private SnakeDirection chosenDirection;
    private String url;
    private ArrayList<Long> timers = new ArrayList<>();
    private int turns = 0;

    private MapCoordinate[] obstacles;
    private ArrayList<MapCoordinate> enemies;
    private ArrayList<MapCoordinate> enemyHeads;
    private ArrayList<MapCoordinate> self;
    private MapUtil mapUtil;


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
        mapUtil = new MapUtil(mapUpdateEvent.getMap(), getPlayerId());

        // import from mapUtil
        enemies = new ArrayList<>();
        enemyHeads = new ArrayList<>();
        self = new ArrayList<>();

        //Get other snake id's and spread
        ArrayList<MapCoordinate[]> otherSnakes = new ArrayList<>();
        SnakeInfo[] snakesInfo = mapUpdateEvent.getMap().getSnakeInfos();
        for(int i = 0; i < snakesInfo.length; i++) {
            String id = snakesInfo[i].getId();
            if(!id.equals(getPlayerId()) && snakesInfo[i].isAlive()) {
                enemyHeads.add(mapUtil.getSnakeSpread(id)[0]);
                for (MapCoordinate coordinate : mapUtil.getSnakeSpread(id)) {
                    enemies.add(coordinate);
                }
            }
        }

        // get own snakespread
        for (MapCoordinate coordinate : mapUtil.getSnakeSpread(getPlayerId())) {
            self.add(coordinate);
        }

        obstacles = mapUtil.listCoordinatesContainingObstacle();

        MapCoordinate pos = mapUtil.getMyPosition();
        MapCoordinate newPos = mapUtil.getMyPosition();

        ArrayList<PathElement> pathOptions = new ArrayList<>();
        List<PathElement> syncedList = Collections.synchronizedList(pathOptions);

        ArrayList<SnakeDirection> availableDirections = new ArrayList<>();

        for (SnakeDirection direction : SnakeDirection.values()) {
            if(mapUtil.canIMoveInDirection(direction))
                    availableDirections.add(direction);
        }

        ExecutorService executor = Executors.newFixedThreadPool(availableDirections.size());

        int id = 0;
        for(SnakeDirection direction : availableDirections) {
            SnakeDirection newDir = null;
            switch (direction){
                case UP:
                    newPos = pos.translateBy(0, -1);
                    newDir = SnakeDirection.UP;
                    break;
                case DOWN:
                    newPos = pos.translateBy(0, 1);
                    newDir = SnakeDirection.DOWN;
                    break;
                case LEFT:
                    newPos = pos.translateBy(-1, 0);
                    newDir = SnakeDirection.LEFT;
                    break;
                case RIGHT:
                    newPos = pos.translateBy(1, 0);
                    newDir = SnakeDirection.RIGHT;
                    break;
            }

            int a = id;
            MapCoordinate newPos1 = newPos;
            SnakeDirection newDir1 = newDir;

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    if(safeTile(newPos1))
                        syncedList.add(new PathElement(
                                newDir1,
                                newPos1,
                                new ArrayList<>(enemies),
                                new ArrayList<>(enemyHeads),
                                new ArrayList<>(self),
                                a,
                                mapUtil));
                }
            });
            id++;
        }

        executor.shutdown();
        try {
            executor.awaitTermination(180, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Collections.sort(pathOptions, new Comparator<PathElement>() {
            @Override
            public int compare(PathElement o1, PathElement o2) {
                if(o1.nodes < o2.nodes) //biggest one first
                    return 1;
                if(o1.nodes == o2.nodes)
                    return 0;
                return -1;
            }
        });

        chosenDirection = pathOptions.get(0).direction; //choose the one with the most nodes

        //Print PathOptions
        int nr = 0;
        for(PathElement option : pathOptions) {
            System.out.print(option.id + ": " + option.direction);
            //System.out.println(", new Pos: " + option.head.toString());
            System.out.print(", nodes: " + option.nodes);

            int sum = 0;
            for (Integer tile : option.enemyTiles) {
                sum += tile;
            }
            if(option.enemyTiles.size() == 0)
                sum = 0;
            else sum /= option.root.enemyTiles.size();
            System.out.print(", Average Enemy tiles: " + sum);

            for (Integer tile : option.ownTiles) {
                sum += tile;
            }
            if(option.ownTiles.size() == 0)
                sum = 0;
            else sum /= option.ownTiles.size();
            System.out.print(", Average Own tiles: " + sum);

            for (Integer tile : option.distToEnemies) {
                sum += tile;
            }
            if(option.distToEnemies.size() == 0)
                sum = 0;
            else sum /= option.distToEnemies.size();
            System.out.print(", Average dist to enemies: " + sum);

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

    private boolean safeTile(MapCoordinate coordinate) {
        boolean res = true;
        // check obstacles
        for (MapCoordinate obstacle : obstacles){
            if(coordinate.equals(obstacle)) {
                res = false;
                break;
            }
        }

        // check enemies
        for (MapCoordinate enemy : enemies){
            if(coordinate.equals(enemy)) {
                res = false;
                break;
            }
        }

        //check self
        for (MapCoordinate me : self){
            if(coordinate.equals(me)) {
                res = false;
                break;
            }
        }

        return !mapUtil.isCoordinateOutOfBounds(coordinate) && res;
    }
}