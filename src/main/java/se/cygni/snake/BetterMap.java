package se.cygni.snake;

import javafx.scene.control.Cell;
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

    public enum CellThing {
        HEAD,
        ENEMYHEAD,
        FOOD,
        OBSTACLE,
        ENEMYSNAKE
    }

    public MapUpdateEvent mapUpdateEvent;
    public CellThing[][] map;
    public int predictedMapStep = 0;
    public int maxPredictSteps = 10; //but is primarily set in SimpleSnakePlayer class

    private MapUtil mapUtil;
    private List<MapCoordinate[]> otherSnakes = new ArrayList<>();
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
                otherSnakes.add(mapUtil.getSnakeSpread(id)); //save coordinate of snakes
            }
        }

        importFromMapUtil();
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
            map[coordinate.x][coordinate.y] = CellThing.OBSTACLE;
        }


    }

    private void predictOtherSnakesPath() {

        for (int x = 0; x < mapWidth; x++) {

            for (int y = 0; y < mapHeight; y++) {

                if (map[x][y].equals(CellThing.ENEMYHEAD)) {
                    //assume they go everywhere at once

                    map[x][y] = CellThing.ENEMYSNAKE;

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

    public void advancePrediction() {
        predictedMapStep++;
        predictOtherSnakesPath();
    }

    public HashMap<SnakeDirection, MapCoordinate> myAvailableMoves() {
        return availableMoves(mapUtil.getMyPosition());
    }

    public HashMap<SnakeDirection, MapCoordinate> availableMoves(MapCoordinate currentCoordinate) {
        MapCoordinate myNewPos = currentCoordinate.translateBy(0, 0);
        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = new HashMap<>();    //HashMap with available moves and the new pos after that
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

    public MapCoordinate[] getObstaclesMapCoordinate() {
        return mapUtil.listCoordinatesContainingObstacle();
    }

    public MapCoordinate[] getFoodMapCoordinate() {
        return mapUtil.listCoordinatesContainingFood();
    }

    public void setCell(MapCoordinate coordinate, CellThing thing) {
        map[coordinate.x][coordinate.y] = thing;
    }

    private boolean isTileAvailableForMovementTo(MapCoordinate coordinate) {
        if(mapUtil.isCoordinateOutOfBounds(coordinate)) return false;
        if(!safeTile(coordinate)) return false;
        return true;
    }

    /*
    Returns true if it is empty or contains food
    In other words, is it safe?
     */
    public boolean safeTile(MapCoordinate coordinate){
        CellThing thing = map[coordinate.x][coordinate.y];
        return (thing == null || thing.equals(CellThing.FOOD));
    }

    /*
    Counts an obstacle as safe
     */
    public boolean safeTileWithObstacle(MapCoordinate coordinate) {
        CellThing thing = map[coordinate.x][coordinate.y];
        return (thing == null || thing.equals(CellThing.FOOD) || thing.equals(CellThing.OBSTACLE) );
    }
}
