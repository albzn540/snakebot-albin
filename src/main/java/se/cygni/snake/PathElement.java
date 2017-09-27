package se.cygni.snake;

import com.sun.xml.internal.bind.v2.TODO;
import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PathElement {
    // -------- Settings -------//
    private int maxPredictSteps = 10;           //how far we will predict
    private int maxEnemyDistCalcStep = 5;       //how far we will count total enemy distance

    /**
     * NOTE! "Obstacle" Counts as "obstacle", "other snakes" and "own snake body".
     * In other words, ANY kind of obstacle.
     */

    //Current direction for this path option
    public SnakeDirection direction;
    //Unique for every path option
    public MapCoordinate head;
    //To the root element, good for searching in this Direction-Tree.
    public PathElement root;

    private ArrayList<MapCoordinate> enemies;
    private ArrayList<MapCoordinate> self;

    //food?

    //root stuff
    public int nodes;
    public int id;
    public MapUtil mapUtil;
    public ArrayList<MapCoordinate> obstacles;
    public ArrayList<Integer> ownTiles;
    public ArrayList<Integer> enemyTiles;
    public ArrayList<Integer> distToEnemies;


    //Root constructor
    public PathElement(SnakeDirection dir, MapCoordinate head, ArrayList<MapCoordinate> enemies, ArrayList<MapCoordinate> enemyHeads, ArrayList<MapCoordinate> self, int id, MapUtil mapUtil) {
        this.id = id;
        this.mapUtil = mapUtil;
        this.obstacles = new ArrayList<>();
        enemyTiles = new ArrayList<>();
        ownTiles = new ArrayList<>();
        distToEnemies = new ArrayList<>();
        for (MapCoordinate coordinate : mapUtil.listCoordinatesContainingObstacle()) {
            this.obstacles.add(coordinate);
        }
        doShit(dir, head, enemies, enemyHeads, self, 0, this);
    }

    public PathElement(SnakeDirection dir, MapCoordinate head, ArrayList<MapCoordinate> enemies, ArrayList<MapCoordinate> enemyHeads, ArrayList<MapCoordinate> self, int currentDepth, PathElement root) {
        doShit(dir, head, enemies, enemyHeads, self, currentDepth, root);
    }

    private void doShit(SnakeDirection dir, MapCoordinate head, ArrayList<MapCoordinate> enemies, ArrayList<MapCoordinate> enemyHeads, ArrayList<MapCoordinate> self, int currentDepth, PathElement root) {

        this.direction = dir;
        this.root = root;
        this.head = new MapCoordinate(head.x, head.y);
        this.enemies =enemies;
        this.self = self;

        // mark last head position as obstacle
        enemies.addAll(enemyHeads);

        // mark self as obstacle
        self.add(head);

        // predict other snakes last move
        ArrayList<MapCoordinate> newHeades = new ArrayList<>();
        for (MapCoordinate enemy : enemyHeads) {
            MapCoordinate newPos = enemy.translateBy(1,0);
            if(safeTile(newPos))
                newHeades.add(newPos);
            newPos = enemy.translateBy(0,1);
            if(safeTile(newPos))
                newHeades.add(newPos);
            newPos = enemy.translateBy(-1,0);
            if(safeTile(newPos))
                newHeades.add(newPos);
            newPos = enemy.translateBy(0,-1);
            if(safeTile(newPos))
                newHeades.add(newPos);
        }


        // if depth not reached, predict own moves
        if(currentDepth < maxPredictSteps) {

            //increment node
            root.nodes++;

            currentDepth++;

            ExecutorService executor = Executors.newFixedThreadPool(3);

            for(SnakeDirection direction : SnakeDirection.values()) {
                MapCoordinate newPos = head;
                SnakeDirection newDir = null;
                switch (direction){
                    case UP:
                        newPos = head.translateBy(0, -1);
                        newDir = SnakeDirection.UP;
                        break;
                    case DOWN:
                        newPos = head.translateBy(0, 1);
                        newDir = SnakeDirection.DOWN;
                        break;
                    case LEFT:
                        newPos = head.translateBy(-1, 0);
                        newDir = SnakeDirection.LEFT;
                        break;
                    case RIGHT:
                        newPos = head.translateBy(1, 0);
                        newDir = SnakeDirection.RIGHT;
                        break;
                }
                if(safeTile(newPos)) {
                    MapCoordinate newPos1 = newPos;
                    SnakeDirection newDir1 = newDir;
                    int currentDepth1 = currentDepth;

                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            new PathElement(
                                    newDir1,
                                    newPos1,
                                    new ArrayList<>(enemies),
                                    new ArrayList<>(enemyHeads),
                                    new ArrayList<>(self),
                                    currentDepth1,
                                    root);

                        }
                    });
                }

            }
            executor.shutdown();
            try {
                executor.awaitTermination(180-currentDepth, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } else {
            root.enemyTiles.add(enemies.size());
            root.ownTiles.add(self.size());
        }

        // calc distance to enemies
        if(currentDepth < maxEnemyDistCalcStep) {
            int shortestDistance = 10000000;
            for (MapCoordinate enemyPart : enemies) {
                if(head.getManhattanDistanceTo(enemyPart) < shortestDistance)
                    shortestDistance = head.getManhattanDistanceTo(enemyPart);
            }
            root.distToEnemies.add(shortestDistance);
        }
    }

    /**
     *
     * @param coordinate
     * @return
     */
    private boolean safeTile(MapCoordinate coordinate) {
        boolean res = true;
        // check obstacles
        for (MapCoordinate obstacle : root.obstacles){
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

        return !root.mapUtil.isCoordinateOutOfBounds(coordinate) && res;
    }
}
