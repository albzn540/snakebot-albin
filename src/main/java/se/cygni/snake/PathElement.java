package se.cygni.snake;

import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.client.MapCoordinate;

import java.lang.reflect.Array;
import java.util.*;

public class PathElement {
    //Current direction for this path option
    public SnakeDirection direction;
    //Arraylist containing
    public ArrayList<PathElement> pathOptions;
    //Unique for every path option
    public MapCoordinate currentCoordinate;
    //To the root element, good for searching in this Direction-Tree.
    public PathElement root;
    //distance to enemies for THIS object
    public int distToEnemies;

    //Root stuff
    public ArrayList<Integer> enemyTiles;
    public ArrayList<Integer> ownTiles;
    public int nodes;
    public int id;

    private BetterMap currentMap;
    private int currentDepth;

    //root constructor
    public PathElement(SnakeDirection dir, MapCoordinate currentCoordinate, BetterMap betterMap, int id){
        distToEnemies = 0;
        enemyTiles = new ArrayList<>();
        ownTiles = new ArrayList<>();
        nodes = -1; //to ensure root has node = 0.
        currentDepth = 0;
        this.id = id;

        constructorHelper(dir, currentCoordinate, betterMap, currentDepth, this);
    }

    public PathElement(SnakeDirection dir, MapCoordinate currentCoordinate, BetterMap betterMap, int currentDepth, PathElement root){
        constructorHelper(dir, currentCoordinate, betterMap, currentDepth, root);
    }

    private void constructorHelper(SnakeDirection dir, MapCoordinate currentCoordinate, BetterMap betterMap, int currentDepth, PathElement root) {
        currentMap = betterMap.copy(betterMap);

        direction = dir; //does not change, don't need to clone
        pathOptions = new ArrayList<>();
        this.currentCoordinate = currentCoordinate;
        this.currentDepth = currentDepth;
        this.root = root;

        //currentmap tests
        //System.out.println("Current map step: " + currentMap.currentMapStep);
        //System.out.println("Current map of root " + root.id + " option " + currentDepth + ": "+ currentMap.toString());

        //increment "size" of path
        root.nodes++;
        //System.out.println("Incremented nodes! New value: " + root.nodes);

        //System.out.println("Reached depth: " + reachedDepth());
        //predict other snakes path and set this coordinate to TAIL
        if(!reachedDepth()) {
            //Predict other snakes
            predictPath();
            //Check our own moves
            checkPossibleMoves();
        }
        else {  //Correct depth reached, calc enemy tiles
            root.enemyTiles.add(betterMap.getEnemyCoordinates().size());
            root.ownTiles.add(betterMap.getMyCoordinates().size());
        }

        //calc dist to enemies.
        distToEnemies = calcShortestDistToEnemies();
    }

    /**
     *
     * @return false if we've reached the wanted depth
     */
    private void predictPath() {
            currentDepth++; //advance depth

            currentMap.advancePrediction(); // The moves the other snakes as they could do the last round
            //Of course it's updated before we check our own moves THIS round

            //Declare this cell as tail, because we will advance...
            currentMap.setCell(currentCoordinate, BetterMap.CellThing.TAIL);
    }

    /**
     *
     * @return true if we we're able to move
     */
    private boolean checkPossibleMoves() {
        //Check available moves THIS round (for the NEXT round).
        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = currentMap.availableMoves(currentCoordinate);
        currentMap.setCell(currentCoordinate, BetterMap.CellThing.HEAD);

        if(dirAndNewPos.isEmpty()) return false;

        for (HashMap.Entry<SnakeDirection, MapCoordinate> option : dirAndNewPos.entrySet()) {
            pathOptions.add(new PathElement(
                    option.getKey(),
                    option.getValue(),
                    currentMap,
                    currentDepth,
                    root));
        }
        return true;
    }

    private int calcShortestDistToEnemies() {
        int shortesDist = 0;
        int thisDist;
        for (MapCoordinate snakePart : currentMap.getEnemyCoordinates()) {
                thisDist = snakePart.getManhattanDistanceTo(currentCoordinate);
                if (thisDist < shortesDist) shortesDist = thisDist;
        }
        return shortesDist;
    }

    public int getDistToEnemies(int distDepth) {
        return distToEnemies + getDistToEnemiesH(distDepth);
    }

    private int getDistToEnemiesH(int distDepth) {
        if(noOptions()) return 0;
        int distToEnemiesDepthSum = 0;
        if(currentDepth < distDepth) {
            for (PathElement option : pathOptions) {
                distToEnemiesDepthSum += option.getDistToEnemies(distDepth-1);
            }
        }
        return distToEnemiesDepthSum / pathOptions.size();
    }

    private boolean noOptions(){
        if(pathOptions.isEmpty()) return true;
        return false;
    }

    private boolean reachedDepth() {
        if(currentDepth < currentMap.maxPredictSteps) return false;
        return true;
    }
}
