package se.cygni.snake;

import se.cygni.snake.client.MapCoordinate;

import java.util.Stack;

public class MaximalTriangle {

    public Rectangle maximalTriangle(BetterMap betterMap) {
        int mapWidth = betterMap.map.length;
        int mapHeight = betterMap.map[0].length;

        int[][] height = new int[mapWidth][mapHeight + 1];
        int maxArea = 0;

        int x = 0;
        int y = 0;
        for (x = 0; x < mapWidth; x++) {

            for (y = 0; y < mapHeight; y++) {

                if (!betterMap.safeTileWithObstacle(new MapCoordinate(x,y))) {
                    height[x][y] = 0;
                } else {
                    height[x][y] = x == 0 ? 1 : height[x - 1][y] + 1;
                }
            }
        }

        for (int i = 0; i < mapHeight; i++) {
            int area = maxAreaInHist(height[i]);
            if (area > maxArea) {
                maxArea = area;
            }
        }

        return new Rectangle(x,y,maxArea);

    }

    private int maxAreaInHist(int[] height) {
        Stack<Integer> stack = new Stack<Integer>();

        int i = 0;
        int max = 0;

        while (i < height.length) {
            if (stack.isEmpty() || height[stack.peek()] <= height[i]) {
                stack.push(i++);
            } else {
                int t = stack.pop();
                max = Math.max(max, height[t]
                        * (stack.isEmpty() ? i : i - stack.peek() - 1));
            }
        }

        return max;
    }

}
