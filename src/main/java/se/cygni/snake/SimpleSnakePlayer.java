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
import se.cygni.snake.client.*;

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

    private static final GameMode GAME_MODE = GameMode.TOURNAMENT;
    private static final String SNAKE_NAME = "Johan är en insider";

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
    private int failsafes = 0;

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
        long startTimer = System.currentTimeMillis();
        ansiPrinter.printMap(mapUpdateEvent);

        // MapUtil contains lot's of useful methods for querying the map!
        mapUtil = new MapUtil(mapUpdateEvent.getMap(), getPlayerId());

        // import from mapUtil
        enemies = new ArrayList<>();
        enemyHeads = new ArrayList<>();
        self = new ArrayList<>();

        int snakesAlive = 0;
        int points = 0;

        //Get other snake id's and spread
        ArrayList<MapCoordinate[]> otherSnakes = new ArrayList<>();
        SnakeInfo[] snakesInfo = mapUpdateEvent.getMap().getSnakeInfos();
        for(int i = 0; i < snakesInfo.length; i++) {
            String id = snakesInfo[i].getId();
            if(id.equals(getPlayerId())) {
                points = snakesInfo[i].getPoints();
            }
            if(!id.equals(getPlayerId()) && snakesInfo[i].isAlive()) {
                snakesAlive++;
                enemyHeads.add(mapUtil.getSnakeSpread(id)[0]);
                for (MapCoordinate coordinate : mapUtil.getSnakeSpread(id)) {
                    enemies.add(coordinate);
                }
            }
        }

        System.out.println("------------------- Turn " + turns + " ------- Points: " + points + " -----------------------");

        // get own snakespread
        for (MapCoordinate coordinate : mapUtil.getSnakeSpread(getPlayerId())) {
            self.add(coordinate);
        }

        obstacles = mapUtil.listCoordinatesContainingObstacle();

        ArrayList<PathElement> pathOptions = new ArrayList<>();
        List<PathElement> syncedList = Collections.synchronizedList(pathOptions);

        ArrayList<SnakeDirection> availableDirections = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (SnakeDirection direction : SnakeDirection.values()) {
            if(mapUtil.canIMoveInDirection(direction))
                    availableDirections.add(direction);
        }

        final int snakesAlive1 = snakesAlive;
        for (int i = 0; i < 60; i++) {
            executor.submit(new Runnable() {
                MapCoordinate pos = mapUtil.getMyPosition();
                MapCoordinate newPos = mapUtil.getMyPosition();
                @Override
                public void run() {
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

                        if(safeTile(newPos))
                            syncedList.add(new PathElement(
                                    newDir,
                                    newPos,
                                    new ArrayList<>(enemies),
                                    new ArrayList<>(enemyHeads),
                                    new ArrayList<>(self),
                                    0,
                                    mapUtil,
                                    snakesAlive1));

                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(!pathOptions.isEmpty()) {
            Collections.sort(syncedList, new Comparator<PathElement>() {
                @Override
                public int compare(PathElement o1, PathElement o2) {
                    if(o1.nodes < o2.nodes) //biggest one first
                        return 1;
                    if(o1.nodes == o2.nodes)
                        return 0;
                    return -1;
                }
            });

            boolean collisionFlag = false;

            int getNumber = 10;
            PathElement best = pathOptions.get(0);
            PathElement current = pathOptions.get(0);
            SnakeDirection betterDirection = null;
            for(int i = 1; i <= getNumber; i++) {

                //check if a collision is about to happen
                if(best.distToEnemies == 0) {
                    collisionFlag = true;
                }

                //start at index one since
                //best node is at index 0 in pathOptions
                current = pathOptions.get(i);
                double percentageOf = 100*((double) current.nodes / (double) best.nodes);
                //System.out.println(percentageOf);
                if(percentageOf > 90 && best.clarity < current.clarity ) {
                    best = current;
                    betterDirection = pathOptions.get(i).direction;
                    //System.out.println("Better direction [1]" + betterDirection);
                }
                else {
                    double clarityDiff = current.clarity - best.clarity;
                    if(percentageOf > 70 && clarityDiff > 300) {
                        best = current;
                        betterDirection = pathOptions.get(i).direction;
                        //System.out.println("Better direction [2]" + betterDirection);
                    }
                }

                if(collisionFlag && percentageOf > 60) {
                    best = current;
                    System.out.println("Collision avoided");
                }
            }


            if(betterDirection != null) {
                chosenDirection = betterDirection;
            }
            else {
                chosenDirection = syncedList.get(0).direction; //choose the one with the most nodes
            }

        } else {
            //Failsafe - Go in direction I can go furthest
            System.out.println("__________________ FAILSAFE! ___________________");
            failsafes++;

            ArrayList<SnakeDirection> newDir = new ArrayList<>();
            ArrayList<Integer> distance = new ArrayList<>();

            for(SnakeDirection dir : availableDirections){
                switch (dir){
                    case RIGHT:
                        distance.add(recursiveDirection(SnakeDirection.RIGHT, mapUtil.getMyPosition()));
                        newDir.add(SnakeDirection.RIGHT);
                        break;
                    case LEFT:
                        distance.add(recursiveDirection(SnakeDirection.LEFT, mapUtil.getMyPosition()));
                        newDir.add(SnakeDirection.LEFT);
                        break;
                    case DOWN:
                        distance.add(recursiveDirection(SnakeDirection.DOWN, mapUtil.getMyPosition()));
                        newDir.add(SnakeDirection.DOWN);
                        break;
                    case UP:
                        distance.add(recursiveDirection(SnakeDirection.UP, mapUtil.getMyPosition()));
                        newDir.add(SnakeDirection.UP);
                        break;
                }
            }

            System.out.println("NewDir size: " + newDir.size() + ", Distance size: " + distance.size());


            int b = 0;
            int index = 0;
            int biggestIndex = 0;
            for(Integer a : distance) {
                if(a>b) {
                    b = a;
                    biggestIndex = index;
                }
                index++;
            }

            if(newDir.size() == 0)
                System.out.println("Can't do shitt, your'e surrounded");
            else if(index == newDir.size())
                chosenDirection = newDir.get(0);
            else chosenDirection = newDir.get(index);
        }


        //Print PathOptions
        int nr = 0;
        int show = 5;
        if(pathOptions.size() < show) show = pathOptions.size();
        for(int i = 0; i < show; i++) {
            System.out.print(pathOptions.get(i).id + ": " + pathOptions.get(i).direction);
            //System.out.println(", new Pos: " + option.head.toString());
            System.out.print(", nodes: " + pathOptions.get(i).nodes);

            int sum = 0;
            for (Integer tile : pathOptions.get(i).enemyTiles) {
                sum += tile;
            }
            if(pathOptions.get(i).enemyTiles.size() == 0)
                sum = 0;
            else sum /= pathOptions.get(i).root.enemyTiles.size();
            System.out.print(", Average Enemy tiles: " + sum);

            for (Integer tile : pathOptions.get(i).ownTiles) {
                sum += tile;
            }
            if(pathOptions.get(i).ownTiles.size() == 0)
                sum = 0;
            else sum /= pathOptions.get(i).ownTiles.size();
            System.out.print(", Average Own tiles: " + sum);

            /*
            for (Integer tile : pathOptions.get(i).distToEnemies) {
                sum += tile;
            }

            if(pathOptions.get(i).distToEnemies.size() == 0)
                sum = 0;
            else sum /= pathOptions.get(i).distToEnemies.size();
            System.out.print(", Average dist to enemies: " + sum); */

            System.out.println(", Dist to enemy : " + pathOptions.get(i).distToEnemies);

            System.out.print(", Last clarity: " + pathOptions.get(i).clarity);

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

        System.out.print("                               ");
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

        Collections.sort(timers);
        Collections.reverse(timers);

        //calc average time
        long tot = 0;
        for (Long time : timers) {
            tot += time;
        }
        long average = tot / timers.size();
        LOGGER.info("Average response time: " + average);
        LOGGER.info("Top 5 worst responsetimes: " + timers.get(0) + ", " + timers.get(1) + ", "+ timers.get(2) + ", "+ timers.get(3));
        LOGGER.info("You survived " + turns + " number of turns.");
        LOGGER.info("Number of failsafes: " + failsafes);
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

    private int recursiveDirection(SnakeDirection dir, MapCoordinate coordinate) {
            switch (dir) {
                case UP:
                    coordinate = coordinate.translateBy(0,-1);
                    break;
                case DOWN:
                    coordinate = coordinate.translateBy(0,1);
                    break;
                case LEFT:
                    coordinate = coordinate.translateBy(-1,0);
                    break;
                case RIGHT:
                    coordinate = coordinate.translateBy(1,0);
                    break;
            }
        if(safeTile(coordinate)) return 1 + recursiveDirection(dir, coordinate);
        return 0;
    }
}