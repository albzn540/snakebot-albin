package se.cygni.snake;

import org.apache.commons.lang3.ArrayUtils;
import se.cygni.snake.api.event.MapUpdateEvent;
import se.cygni.snake.api.model.Map;
import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.api.model.SnakeInfo;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER;

public class BetterMap {

    /**
     *  This class will handle map-related things.
     *  Such as:
     *      Predictions
     *      Snake Positions
     *      Obstacles
     *      Enemy tiles
     *      Own tiles
     *      Distances
     */

    public enum CellThing {
        HEAD,
        ENEMYHEAD,
        FOOD,
        OBSTACLE,
        ENEMYSNAKE,
        TAIL,
        EMPTY
    }

    public MapUpdateEvent mapUpdateEvent;
    public CellThing[][] map;
    public int currentMapStep = 0;
    public int maxPredictSteps = 10; //but is primarily set in SimpleSnakePlayer class
    public List<MapCoordinate[]> otherSnakes = new ArrayList<>();

    private MapUtil mapUtil;
    private int mapHeight;
    private int mapWidth;
    private String playerID;


    public BetterMap(Map mapUpdateEvent, String playerID) {

        mapHeight = mapUpdateEvent.getHeight();
        mapWidth = mapUpdateEvent.getWidth();
        this.playerID = playerID;

        map = new CellThing[mapWidth][mapHeight];
        mapUtil = new MapUtil(mapUpdateEvent, playerID);

        //Get other snake id's and spread
        SnakeInfo[] snakesInfo = mapUpdateEvent.getSnakeInfos();
        for(int i = 0; i < snakesInfo.length; i++) {
            String id = snakesInfo[i].getId();
            if(!id.equals(playerID) && snakesInfo[i].isAlive()) {
                otherSnakes.add(mapUtil.getSnakeSpread(id)); //save coordinates of snakes as MapCoordinate
            }
        }

        //Import food, obstacles, other snakes(head, body) and lastly own body
        //Sets remaining to empty
        importFromMapUtil();
    }

    public BetterMap copy(BetterMap oldMap) {
        BetterMap newMap = new BetterMap();

        //copy content
        newMap.mapHeight = oldMap.mapHeight;
        newMap.mapWidth = oldMap.mapWidth;
        newMap.currentMapStep = oldMap.currentMapStep;
        newMap.playerID = oldMap.playerID;

        newMap.mapUtil = oldMap.mapUtil;
        newMap.map = new CellThing[mapWidth][mapHeight];

        //coppy shitty map-shitt
        for(int x = 0; x < mapWidth; x++) {
            for(int y = 0; y < mapHeight; y++) {
                newMap.map[x][y] = oldMap.map[x][y];
            }
        }

        return newMap;
    }

    public BetterMap() {
    }

    private void importFromMapUtil() {
        //imports food
        for (MapCoordinate coordinate : mapUtil.listCoordinatesContainingFood()) {
            map[coordinate.x][coordinate.y] = CellThing.FOOD;
        }

        //import obstacles
        for (MapCoordinate coordinate : mapUtil.listCoordinatesContainingObstacle()) {
            map[coordinate.x][coordinate.y] = CellThing.OBSTACLE;
        }

        //import other snakes
        for (MapCoordinate[] snake : otherSnakes) {
            //head
            map[snake[0].x][snake[0].y] = CellThing.ENEMYHEAD;

            //body
            for(int i = 1; i < snake.length; i++) {
                map[snake[i].x][snake[i].y] = CellThing.ENEMYSNAKE;
            }
        }

        //import own body
        MapCoordinate[] ownSnake = mapUtil.getSnakeSpread(playerID);
        for (MapCoordinate coordinate : ownSnake){
            map[coordinate.x][coordinate.y] = CellThing.TAIL;           // <------- Problem? Should I set from neck to tail?
        }

        //set others to empty
        for(int x = 0; x < mapWidth; x++) {
            for(int y = 0; y < mapHeight; y++) {
                if(map[x][y] == null)
                    map[x][y] = CellThing.EMPTY;
            }
        }


    }

    private void predictOtherSnakesPath() {
        //have to do it like this, beacuse of the predictions.
        //We don't store snakeheads in a list between them.
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {

                if (map[x][y].equals(CellThing.ENEMYHEAD)) {
                    //assume they go everywhere at once

                    map[x][y] = CellThing.ENEMYSNAKE;           //ta i åtanke att de kan dö? och att de försvinner då?

                    //have to check if its safe to go to before moving
                    MapCoordinate newPos = new MapCoordinate(x+1,y);
                    if(isTileAvailableForMovementTo(newPos))
                        setCell(newPos, CellThing.ENEMYHEAD);

                    newPos = new MapCoordinate(x,y+1);
                    if(isTileAvailableForMovementTo(newPos))
                        setCell(newPos, CellThing.ENEMYHEAD);

                    newPos = new MapCoordinate(x-1,y);
                    if(isTileAvailableForMovementTo(newPos))
                        setCell(newPos, CellThing.ENEMYHEAD);

                    newPos = new MapCoordinate(x,y-1);
                    if(isTileAvailableForMovementTo(newPos))
                        setCell(newPos, CellThing.ENEMYHEAD);
                }
            }
        }

    }

    /**
     *
     * @return Arraylist with all the enemies MapCoordinates
     */
    public ArrayList<MapCoordinate> getEnemyCoordinates() {
        ArrayList<MapCoordinate> result = new ArrayList<>();
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                if(map[x][y].equals(CellThing.ENEMYHEAD) || map[x][y].equals(CellThing.ENEMYSNAKE))
                    result.add(new MapCoordinate(x, y));
            }
        }
        return result;
    }

    public void advancePrediction() {
        currentMapStep++;
        predictOtherSnakesPath();
    }

    public HashMap<SnakeDirection, MapCoordinate> availableMoves(MapCoordinate currentCoordinate) {

        //Somewhere to store our results
        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = new HashMap<>();

        MapCoordinate myNewPos = currentCoordinate.translateBy(0, 0); //The current coordinate we're on.
        // Let's see in which directions I can move
        for (SnakeDirection direction : SnakeDirection.values()) {
            try {
                switch (direction) {
                    case DOWN:
                        myNewPos = currentCoordinate.translateBy(0, 1);
                        break;
                    case UP:
                        myNewPos = currentCoordinate.translateBy(0, -1);
                        break;
                    case LEFT:
                        myNewPos = currentCoordinate.translateBy(-1, 0);
                        break;
                    case RIGHT:
                        myNewPos = currentCoordinate.translateBy(1, 0);
                }

                if(isTileAvailableForMovementTo(myNewPos))
                    dirAndNewPos.put(direction, myNewPos);

            } catch (Exception e) {
                LOGGER.info("Error in evaluating your path" + e);
            }
        }

        return dirAndNewPos;
    }

    public void setCell(MapCoordinate coordinate, CellThing thing) {
        map[coordinate.x][coordinate.y] = thing;
    }

    public boolean isTileAvailableForMovementTo(MapCoordinate coordinate) {
        if(mapUtil.isCoordinateOutOfBounds(coordinate)) return false;
        if(!safeTile(coordinate)) return false;
        return true;
    }

    public ArrayList<MapCoordinate> getMyCoordinates() {
        ArrayList<MapCoordinate> snake = new ArrayList<>();
        for(int x = 0; x < mapWidth;x++) {
            for(int y = 0; y < mapHeight;y++) {
                if(map[x][y].equals(CellThing.HEAD) || map[x][y].equals(CellThing.TAIL))
                    snake.add(new MapCoordinate(x, y));
            }
        }
        return snake;
    }

    public MapCoordinate[] getObstaclesMapCoordinate() {
        //obstacles won't move around, this is no problem.
        return mapUtil.listCoordinatesContainingObstacle();
    }

    public MapCoordinate[] getObstaclesAndEnemiesMapCoordinates() {
        ArrayList<MapCoordinate> otherSnakesList = new ArrayList<>();
        for(int x = 0; x < mapWidth;x++) {
            for(int y = 0; y < mapHeight;y++) {
                if(map[x][y].equals(CellThing.ENEMYHEAD) || map[x][y].equals(CellThing.ENEMYSNAKE))
                    otherSnakesList.add(new MapCoordinate(x, y));
            }
        }
        return (MapCoordinate[]) ArrayUtils.addAll(mapUtil.listCoordinatesContainingObstacle(), otherSnakesList);
    }

    public MapCoordinate[] getFoodMapCoordinate() {
        //Does not need to be updated between predictions?
        return mapUtil.listCoordinatesContainingFood();
    }

    /**
    Returns true if it is empty or contains food
    In other words, is it safe?
     */
    private boolean safeTile(MapCoordinate coordinate){
        CellThing thing = map[coordinate.x][coordinate.y];
        return (thing.equals(CellThing.EMPTY) || thing.equals(CellThing.FOOD)); //end-tail as well?
    }

    /**
    Counts even obstacle as "safe"
     */
    public boolean safeTileOrObstacle(MapCoordinate coordinate) {
        CellThing thing = map[coordinate.x][coordinate.y];
        return (thing.equals(CellThing.EMPTY) || thing.equals(CellThing.FOOD) || thing.equals(CellThing.OBSTACLE) );
    }
}
