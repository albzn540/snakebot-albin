package se.cygni.snake;

import se.cygni.snake.api.model.MapSnakeHead;
import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.api.model.SnakeInfo;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class PathElement {

    public SnakeDirection direction;
    public ArrayList<PathElement> childs = null;

    MapUtil mapUtil;

    public PathElement(SnakeDirection dir, MapCoordinate head, MapCoordinate[] obstacles, ArrayList<MapCoordinate> predictedSnakes, int depth){
        direction = dir;

        List<SnakeDirection> directions = new ArrayList<>();

        // Let's see in which directions I can move
        for (SnakeDirection direction : SnakeDirection.values()) {

            //must check if it will collide with predictedSnakes
            if (mapUtil.canIMoveInDirection(direction)) {
                directions.add(direction);
            }
        }

        if(!directions.isEmpty() && depth > 0) {
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
                if(!mapUtil.isCoordinateOutOfBounds(newRight))
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

            childs = new ArrayList<PathElement>();
            for (SnakeDirection childDir : directions) {
                childs.add(new PathElement(childDir, obstacles, predictedSnakes, depth));
             }
        }
    }

    public boolean noChild(){
        if(childs == null) return true;
        return false;
    }
}
