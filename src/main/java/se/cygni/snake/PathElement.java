package se.cygni.snake;

import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.ArrayList;
import java.util.Random;

public class PathElement {
    // -------- Settings -------//
    private int maxPredictSteps = 180;           //how far we will predict
    private int maxEnemyDistCalcStep = 0;       //how far we will count total enemy distance

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
    private ArrayList<MapCoordinate> newHeades;

    //food?

    //root stuff
    public int nodes;
    public int id;
    public MapUtil mapUtil;
    public ArrayList<MapCoordinate> obstacles;
    public ArrayList<Integer> ownTiles;
    public ArrayList<Integer> enemyTiles;
    public ArrayList<Integer> distToEnemies;
    public int clarity;


    //Root constructor
    public PathElement(SnakeDirection dir, MapCoordinate head, ArrayList<MapCoordinate> enemies, ArrayList<MapCoordinate> enemyHeads, ArrayList<MapCoordinate> self, int id, MapUtil mapUtil, int snakesAlive) {
        this.id = id;
        this.mapUtil = mapUtil;
        this.obstacles = new ArrayList<>();
        enemyTiles = new ArrayList<>();
        ownTiles = new ArrayList<>();
        distToEnemies = new ArrayList<>();

        if(snakesAlive == 2) {
            maxPredictSteps = 200;
        }

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
       newHeades = new ArrayList<>();
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

            ArrayList<SnakeDirection> dirlist = new ArrayList<>();
            ArrayList<MapCoordinate> poslist = new ArrayList<>();
            Random rnd = new Random();
            MapCoordinate newPos = head;

            for (SnakeDirection direction : SnakeDirection.values()) {
                switch (direction) {
                    case UP:
                        newPos = head.translateBy(0, -1);
                        break;
                    case DOWN:
                        newPos = head.translateBy(0, 1);
                        break;
                    case LEFT:
                        newPos = head.translateBy(-1, 0);
                        break;
                    case RIGHT:
                        newPos = head.translateBy(1, 0);
                        break;
                }
                if (safeTile(newPos)) {
                    dirlist.add(direction);
                    poslist.add(newPos);
                }
            }

            if (!dirlist.isEmpty()) {
                int index = rnd.nextInt(dirlist.size());
                new PathElement(
                        dirlist.get(index),
                        poslist.get(index),
                        new ArrayList<>(enemies),
                        new ArrayList<>(enemyHeads),
                        new ArrayList<>(self),
                        currentDepth,
                        root);
            } else {
                reachedEnd();
            }
        }
        else {
            reachedEnd();
        }
        //long start = System.currentTimeMillis();
        //calc clarity
        //straight line
        ArrayList<SnakeDirection> dirlist = new ArrayList<>();
        ArrayList<MapCoordinate> poslist = new ArrayList<>();
        MapCoordinate newPos = head;

        for (SnakeDirection direction : SnakeDirection.values()) {
            switch (direction) {
                case UP:
                    newPos = head.translateBy(0, -1);
                    for(int i = 0; i < 50; i++) {
                        if(safeTile(newPos)) {
                            root.clarity++;
                            newPos = newPos.translateBy(0,-1);
                        }
                    }
                    break;
                case DOWN:
                    newPos = head.translateBy(0, 1);
                    for(int i = 0; i < 50; i++) {
                        if(safeTile(newPos)) {
                            root.clarity++;
                            newPos = newPos.translateBy(0,1);
                        }
                    }
                    break;
                case LEFT:
                    newPos = head.translateBy(-1, 0);
                    for(int i = 0; i < 50; i++) {
                        if(safeTile(newPos)) {
                            root.clarity++;
                            newPos = newPos.translateBy(-1,0);
                        }
                    }
                    break;
                case RIGHT:
                    newPos = head.translateBy(1, 0);
                    for(int i = 0; i < 50; i++) {
                        if(safeTile(newPos)) {
                            root.clarity++;
                            newPos = newPos.translateBy(1,0);
                        }
                    }
                    break;
            }

        }
        //long time = System.currentTimeMillis() - start;
        //System.out.println("Time to calc clarity: "+time);

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

    private void reachedEnd() {
        root.enemyTiles.add(enemies.size());
        root.ownTiles.add(self.size());
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

        //check enemy heads
        for (MapCoordinate head : newHeades){
            if(coordinate.equals(head)) {
                res = false;
                break;
            }
        }

        return !root.mapUtil.isCoordinateOutOfBounds(coordinate) && res;
    }
}