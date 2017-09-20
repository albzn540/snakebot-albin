package se.cygni.snake;

import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.*;

public class PathElement {

    public SnakeDirection direction;
    public ArrayList<PathElement> childs = null;
    public MapCoordinate currentCoordinate;                    //Unique for every three


    public PathElement(SnakeDirection dir, MapCoordinate head, MapCoordinate[] obstacles, List<MapCoordinate> predictedSnakes, int depth, SimpleSnakePlayer.WrapInt sizePointer, MapUtil mapUtil){
        direction = dir;
        currentCoordinate = head;
        sizePointer.value++;

        System.out.println("SnakePOS:" + mapUtil.getMyPosition().toString());
        MapCoordinate myNewPos = currentCoordinate.translateBy(0, 0);           //My new pos based on direction
        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = new HashMap<>();    //HashMap with available moves and the new pos after that

        ArrayList<MapCoordinate> copyOfOtherSnakeHead = new ArrayList<>(predictedSnakes);

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

                if(mapUtil.isTileAvailableForMovementTo(myNewPos)) {
                    dirAndNewPos.put(direction, myNewPos);
                }

            } catch (Exception e) {
                //Nothing
            }
        }

        System.out.println("Dir: " + dir);
        System.out.println("Number of childs: " + dirAndNewPos.values().size());

        if(!dirAndNewPos.isEmpty() && depth > 0) {
            depth--;

            //Create predictions for the other snakes
            List<MapCoordinate> newPredictedSnakes = new ArrayList<>();
            for (MapCoordinate p : predictedSnakes) {
                //check up
                MapCoordinate newUp = new MapCoordinate(p.x, p.y + 1);
                if(!mapUtil.isCoordinateOutOfBounds(newUp))
                    newPredictedSnakes.add(newUp);

                //check right
                MapCoordinate newRight = new MapCoordinate(p.x + 1, p.y);
                if (!mapUtil.isCoordinateOutOfBounds(newRight))
                    newPredictedSnakes.add(newRight);

                //check down
                MapCoordinate newDown = new MapCoordinate(p.x, p.y - 1);
                if(!mapUtil.isCoordinateOutOfBounds(newDown))
                    newPredictedSnakes.add(newDown);

                //check left
                MapCoordinate newLeft = new MapCoordinate(p.x - 1, p.y);
                if(!mapUtil.isCoordinateOutOfBounds(newLeft))
                    newPredictedSnakes.add(newLeft);
            }

            predictedSnakes.addAll(newPredictedSnakes);

            for (HashMap.Entry<SnakeDirection, MapCoordinate> entry : dirAndNewPos.entrySet()) {
                childs.add(new PathElement(
                        entry.getKey(),
                        entry.getValue(),
                        obstacles,
                        copyOfOtherSnakeHead,
                        depth,
                        sizePointer,
                        mapUtil));
            }
        }
    }

    public boolean noChild(){
        if(childs.isEmpty()) return true;
        return false;
    }

    public boolean equals(MapCoordinate coordinate){
        return currentCoordinate.equals(coordinate);
    }
}
