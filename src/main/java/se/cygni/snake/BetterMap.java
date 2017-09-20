package se.cygni.snake;

import se.cygni.snake.api.event.MapUpdateEvent;
import se.cygni.snake.api.model.SnakeInfo;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.ArrayList;
import java.util.List;

public class BetterMap {

    public enum CellThing {
        head,
        enemyhead,
        food,
        obstacle
    }

    public MapUpdateEvent mapUpdateEvent;
    public CellThing[][] predictedMap;
    public int predictedMapStep = 0;

    private MapUtil mapUtil;

    public BetterMap(MapUpdateEvent mapUpdateEvent, String playerID) {

        this.mapUpdateEvent = mapUpdateEvent;

        mapUtil = new MapUtil(mapUpdateEvent.getMap(), playerID);

        //Get other snake id's
        List<MapCoordinate> otherSnakeHeads = new ArrayList<>();
        SnakeInfo[] snakesInfo = mapUpdateEvent.getMap().getSnakeInfos();
        for(int i = 0; i < snakesInfo.length; i++) {
            String id = snakesInfo[i].getId();
            if(id.equals(playerID) {
                otherSnakeHeads.add(mapUtil.getSnakeSpread(id)[0]);
            }
        }
    }

    private void predictOtherSnakesPath() {

    }

    public void advancePrediction() {
        predictedMapStep++;
        predictOtherSnakesPath();
    }

    public MapCoordinate[] getObstacles() {
        return mapUtil.listCoordinatesContainingObstacle();
    }

    public MapCoordinate[] getFood() {
        return mapUtil.listCoordinatesContainingFood();
    }

    public void isSafeToGoTo() {

    }
}
