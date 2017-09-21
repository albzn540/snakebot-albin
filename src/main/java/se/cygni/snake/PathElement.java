package se.cygni.snake;

import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

import java.util.*;

public class PathElement {

    public SnakeDirection direction;
    public ArrayList<PathElement> childs = new ArrayList<>();
    public MapCoordinate currentCoordinate;                    //Unique for every three

    public PathElement(SnakeDirection dir, MapCoordinate currentCoordinate, BetterMap betterMap, int depth, SimpleSnakePlayer.WrapInt sizePointer){
        direction = dir;
        sizePointer.increment();

        HashMap<SnakeDirection, MapCoordinate> dirAndNewPos = betterMap.availableMoves(currentCoordinate);

        if(!(dirAndNewPos.isEmpty()) && (depth < betterMap.maxPredictSteps)) {
            depth++;
            if(depth < betterMap.predictedMapStep)
                betterMap.advancePrediction();

            for (HashMap.Entry<SnakeDirection, MapCoordinate> child : dirAndNewPos.entrySet()) {
                childs.add(new PathElement(
                        child.getKey(),
                        child.getValue(),
                        betterMap,
                        depth,
                        sizePointer));
            }
        }
    }

    public boolean noChild(){
        if(childs.isEmpty()) return true;
        return false;
    }
}
