package com.ff.heatmap.heatmap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import java.util.Collection;

/**
 * 热力图
 */
public class HeatMap {
    private static final String TAG = "HeatMap";

    private static final double DEFAULT_OPACITY = 1;

    private static final int DEFAULT_RADIUS = 36;

//    private static final Gradient DEFAULT_GRADIENT = new Gradient(
//            new int[]{Color.TRANSPARENT,
//                    Color.argb(40, 255, 255, 128),
//                    Color.argb(80, 255, 255, 128),
//                    Color.argb(100, 255, 255, 128),
//                    Color.argb(200, 255, 255, 128),
//                    Color.argb(80, 255, 159, 0),
//                    Color.argb(100, 255, 159, 0),
//                    Color.argb(200, 255, 159, 0),
//                    Color.argb(100, 255, 128, 0),
//                    Color.argb(200, 255, 128, 0),
//                    Color.argb(150, 255, 32, 0),
//                    Color.argb(200, 255, 32, 0)},
//            new float[]{0f,0.01f, 0.10f, 0.20f, 0.30f, 0.40f, 0.5f, 0.60f, 0.7f, 0.80f, 0.90f, 1f});

    private static final Gradient DEFAULT_GRADIENT = new Gradient(
            new int[]{Color.TRANSPARENT,
                    Color.argb(80, 255, 255, 128),
                    Color.argb(255, 251, 241, 90),
                    Color.argb(255, 235, 136, 39),
                    Color.argb(255, 254, 227, 97),
                    Color.argb(255, 237, 63, 44)},
            new float[]{0f, 0.001f, 0.25f, 0.50f, 0.75f, 1f});

    private static final int MIN_RADIUS = 10;

    private static final int MAX_RADIUS = 50;

    private Collection<WeightedLatLng> mData;

    private int mRadius;

    private Gradient mGradient;

    private int[] mColorMap;

    private double[] mKernel;

    private double mOpacity;

    private double mMaxIntensity;

    private static int mWidth;

    private static int mHeight;

    private HeatMap(Builder builder) {
        mData = builder.data;

        mRadius = builder.radius;
        mGradient = builder.gradient;
        mOpacity = builder.opacity;

        mWidth = builder.width;
        mHeight = builder.height;

        mKernel = generateKernel(mRadius, mRadius / 3.0);

        setGradient(mGradient);

        setWeightedData(mData);
    }

    static double[] generateKernel(int radius, double sd) {
        double[] kernel = new double[radius * 2 + 1];
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (Math.exp(-i * i / (2 * sd * sd)));
        }
        return kernel;
    }

    static double[][] convolve(double[][] grid, double[] kernel) {
        int radius = (int) Math.floor((double) kernel.length / 2.0);

        int dimOldW = grid.length;
        int dimOldH = grid[0].length;

        int dimW = dimOldW - 2 * radius;
        int dimH = dimOldH - 2 * radius;

        int lowerLimit = radius;
        int upperLimitW = radius + dimW - 1;
        int upperLimitH = radius + dimH - 1;


        double[][] intermediate = new double[dimOldW][dimOldH];

        int x, y, x2, xUpperLimit, initial;
        double val;
        for (x = 0; x < dimOldW; x++) {
            for (y = 0; y < dimOldH; y++) {
                val = grid[x][y];
                if (val != 0) {
                    xUpperLimit = ((upperLimitW < x + radius) ? upperLimitW : x + radius) + 1;
                    //解决四个顶点不显示的问题
                    initial = (lowerLimit - radius > x - radius) ? lowerLimit - radius : x - radius;
                    for (x2 = initial; x2 < xUpperLimit; x2++) {
                        intermediate[x2 + radius][y + radius] += val * kernel[x2 - (x - radius)];
                    }
                }
            }
        }

        double[][] outputGrid = new double[dimW][dimH];

        int y2, yUpperLimit;

        for (x = lowerLimit; x < upperLimitW + 1; x++) {
            for (y = 0; y < dimOldH; y++) {
                val = intermediate[x][y];
                if (val != 0) {
                    yUpperLimit = ((upperLimitH < y + radius) ? upperLimitH : y + radius) + 1;
                    initial = (lowerLimit > y - radius) ? lowerLimit : y - radius;
                    for (y2 = initial; y2 < yUpperLimit; y2++) {
                        outputGrid[x - radius][y2 - radius] += val * kernel[y2 - (y - radius)];
                    }
                }
            }
        }

        return outputGrid;
    }

    static Bitmap colorize(double[][] grid, int[] colorMap, double max) {
        int maxColor = colorMap[colorMap.length - 1];
        double colorMapScaling = (colorMap.length - 1) / max;

        int dimW = grid.length;
        int dimH = grid[0].length;

        int i, j, index, col;
        double val;
        int colors[] = new int[dimW * dimH];
        for (i = 0; i < dimH; i++) {
            for (j = 0; j < dimW; j++) {
                val = grid[j][i];
                index = i * dimW + j;
                col = (int) (val * colorMapScaling);

                if (val != 0) {
                    if (col < colorMap.length) colors[index] = colorMap[col];
                    else colors[index] = maxColor;
                } else {
                    colors[index] = Color.TRANSPARENT;
                }
            }
        }

        Bitmap tile = Bitmap.createBitmap(dimW, dimH, Bitmap.Config.ARGB_8888);
        tile.setPixels(colors, 0, dimW, 0, 0, dimW, dimH);
        return tile;
    }

    public void setWeightedData(Collection<WeightedLatLng> data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("No input points.");
        }
        mData = data;
        mMaxIntensity = getMaxIntensities();
    }

    public Bitmap generateMap() {
        double[][] intensity = new double[mWidth + mRadius * 2][mHeight + mRadius * 2];
        for (WeightedLatLng w : mData) {
            //if you are using LatLng,transform them into screen coordinates
            int bucketX = w.x;
            int bucketY = w.y;
            if (bucketX <= mWidth && bucketX >= 0
                    && bucketY <= mHeight && bucketY >= 0)
                intensity[bucketX][bucketY] += w.intensity;
        }

        double[][] convolved = convolve(intensity, mKernel);

        return colorize(convolved, mColorMap, mMaxIntensity);
    }

    public void setGradient(Gradient gradient) {
        mGradient = gradient;
        mColorMap = gradient.generateColorMap(mOpacity);
    }

    public void setRadius(int radius) {
        mRadius = radius;
        mKernel = generateKernel(mRadius, mRadius / 3.0);
        mMaxIntensity = getMaxIntensities();
    }

    public void setOpacity(double opacity) {
        mOpacity = opacity;
        setGradient(mGradient);
    }

    private double getMaxIntensities() {
        double maxIntensity = 0;

        for (WeightedLatLng l : mData) {
            double value = l.intensity;
            if (value > maxIntensity) maxIntensity = value;
        }
        return maxIntensity;
    }

    public static class Builder {
        private Collection<WeightedLatLng> data;

        private int radius = DEFAULT_RADIUS;
        private Gradient gradient = DEFAULT_GRADIENT;
        private double opacity = DEFAULT_OPACITY;
        private int width = 0;
        private int height = 0;

        public Builder() {
        }

        public Builder weightedData(Collection<WeightedLatLng> val) {
            this.data = val;

            if (this.data.isEmpty()) {
                throw new IllegalArgumentException("No input points.");
            }
            return this;
        }

        public Builder radius(int val) {
            radius = val;
            if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
                throw new IllegalArgumentException("Radius not within bounds.");
            }
            return this;
        }

        public Builder gradient(Gradient val) {
            gradient = val;
            return this;
        }

        public Builder opacity(double val) {
            opacity = val;
            if (opacity < 0 || opacity > 1) {
                throw new IllegalArgumentException("Opacity must be in range [0, 1]");
            }
            return this;
        }

        public Builder width(int val) {
            this.width = val;
            if (width <= 0) {
                throw new IllegalArgumentException("Width must be above 0");
            }
            return this;
        }

        public Builder height(int val) {
            this.height = val;
            if (height <= 0) {
                throw new IllegalArgumentException("Height must be above 0");
            }
            return this;
        }

        public HeatMap build() {
            if (data == null || width == 0 || height == 0) {
                throw new IllegalStateException("No input data: you must use .weightedData&&.width&&.height before building");
            }

            return new HeatMap(this);
        }
    }
}
