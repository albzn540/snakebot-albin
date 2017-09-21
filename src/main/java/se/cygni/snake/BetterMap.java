package se.cygni.snake;

import se.cygni.snake.api.event.MapUpdateEvent;
import se.cygni.snake.api.model.SnakeInfo;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.ArrayList;
import java.util.List;

public class BetterMap {

    public enum CellThing {
        HEAD,
        ENEMYHEAD,
        FOOD,
        OBSTACLE,
        EMPTY
    }

    public MapUpdateEvent mapUpdateEvent;
    public CellThing[][] map;
    public int predictedMapStep = 0;

    private MapUtil mapUtil;
    private List<MapCoordinate> otherSnakeHeads = new ArrayList<>();
    private int mapHeight;
    private int mapWidth;


    public BetterMap(MapUpdateEvent mapUpdateEvent, String playerID) {

        this.mapUpdateEvent = mapUpdateEvent;
        mapHeight = mapUpdateEvent.getMap().getHeight();
        mapWidth = mapUpdateEvent.getMap().getWidth();

        mapUtil = new MapUtil(mapUpdateEvent.getMap(), playerID);

        //Get other snake id's and heads

        SnakeInfo[] snakesInfo = mapUpdateEvent.getMap().getSnakeInfos();
        for(int i = 0; i < snakesInfo.length; i++) {
            String id = snakesInfo[i].getId();
            if(id.equals(playerID)) {
                otherSnakeHeads.add(mapUtil.getSnakeSpread(id)[0]); //save coordinate of snake heads
            }
        }


    }

    private void importFromMapUtil() {
        mapUpdateEvent.getMap().getFoodPositions();
        mapUtil.listCoordinatesContainingFood();

        //imports food
        for (MapCoordinate coordinate : mapUtil.listCoordinatesContainingFood()) {
            map[coordinate.x][coordinate.y] = CellThing.FOOD;
        }

        //import obstacles
        //is the rest of the snakebody an obstacle?
        for (MapCoordinate coordinate : mapUtil.listCoordinatesContainingObstacle()) {
            map[coordinate.x][coordinate.y] = CellThing.OBSTACLE;
        }

        for (MapCoordinate coordinate : otherSnakeHeads) {
            map[coordinate.x][coordinate.y] = CellThing.ENEMYHEAD;
        }

    }

    private void predictOtherSnakesPath() {

        for (int x = 0; x < mapWidth; x++) {

            for (int y = 0; y < mapHeight; y++) {

                if (map[x][y].equals(CellThing.ENEMYHEAD)) {
                    //assume they go everywhere at once

                    map[x][y] = CellThing.OBSTACLE;

                    //have to check if its available to go to before
                    if(x+1 < mapWidth) map[x+1][y] = CellThing.ENEMYHEAD;
                    if(x-1 >= 0) map[x-1][y] = CellThing.ENEMYHEAD;
                    if(y+1 < mapHeight) map[x][y+1] = CellThing.ENEMYHEAD;
                    if(y-1 >= 0) map[x][y-1] = CellThing.ENEMYHEAD;

                    if (x < mapWidth && x >= 0) {

                    }
                }
            }
        }

    }

    public void advancePrediction() {
        predictedMapStep++;
        predictOtherSnakesPath();
    }

    public MapCoordinate[] getObstaclesMapCoordinate() {
        return mapUtil.listCoordinatesContainingObstacle();
    }

    public MapCoordinate[] getFoodMapCoordinate() {
        return mapUtil.listCoordinatesContainingFood();
    }

    public void isSafeToGoTo() {

    }
}
