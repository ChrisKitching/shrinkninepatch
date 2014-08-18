package ckitching;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.io.File;
import java.io.IOException;

public class Main {
    public static final int ARGB_BLACK = 0xFF000000;

    /**
     * Takes a list of nine patch images and overwrites them with optimised ones.
     * Where the scalable region of a nine-patch is identical across the dimension to be scaled, it
     * may be replaced by a single row or column without altering the resulting image (provided
     * only one scalable region exists. A future extension may handle multiple ones and scaling in
     * a ratio-preserving way, but I don't currently care).
     */
    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].endsWith(".9.png")) {
                shrinkNinePatch(args[i]);
            }
        }
    }

    public static void shrinkNinePatch(String image) {
        System.out.println("Processing " + image);
        int[][] pixels = loadPixels(image);

        // x and y represent the new size of the image.
        Point newSize = optimisePixels(pixels);
        if (newSize == null) {
            return;
        }

        int width = newSize.x;
        int height = newSize.y;

        int[] finalBuffer;
        finalBuffer = collapseAndFlattenBuffer(pixels, width, height);
        writeImage(finalBuffer, width, height, image);
    }

    private static int[][] loadPixels(String image) {
        Image img;
        try {
            img = ImageIO.read(new File(image));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        int w = img.getWidth(null);
        int h = img.getHeight(null);
        int[] pixels = new int[w * h];
        PixelGrabber pg = new PixelGrabber(img, 0, 0, w, h, pixels, 0, w);

        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
            System.err.println("interrupted waiting for pixels!");
            System.exit(1);
            return null;
        }

        if ((pg.getStatus() & ImageObserver.ABORT) != 0) {
            System.err.println("image fetch aborted or errored");
            System.exit(2);
            return null;
        }


        // Now we do the slightly evil but sanity-preserving thing of copying all the pixels into an
        // int[][].

        int[][] finalPixels = new int[h][w];
        // Assuming left-to-right top-to-bottom...
        for (int i = 0; i < h; i++) {
            // Copy a row across...
            System.arraycopy(pixels, i * w, finalPixels[i], 0, w);
        }

        return finalPixels;
    }

    /**
     * Run the optimisation.
     */
    public static Point optimisePixels(int[][] pixels) {
        if (hasMultipleScalableRegions(pixels)) {
            System.err.println("Multiple scalable regions found: ignoring!");
            return null;
        }

        // Find start of scalable region.
        int topScalableStart = 0;
        while (true) {
            if (pixels[0][topScalableStart] == ARGB_BLACK) {
                break;
            }

            topScalableStart++;
        }

        // Find the end...
        int topScalableEnd = topScalableStart + 1;
        while (topScalableEnd < pixels[0].length) {
            if (pixels[0][topScalableEnd] != ARGB_BLACK) {
                break;
            }

            topScalableEnd++;
        }

        int xCollapsed = 0;
        // If it's a one pixel region, we have nothing to do.
        if (topScalableEnd != topScalableStart + 1) {
            // Scan all columns from here to ensure they're all the same.
            if (columnRangeMatches(pixels, topScalableStart, topScalableEnd)) {

                // Hooray, it turns out that the detected region can safely be collapsed.
                collapseColumnRange(pixels, topScalableStart, topScalableEnd);
                xCollapsed = topScalableEnd - topScalableStart - 1;
            } else {
                System.err.println("Column range doesn't match: ignoring!");
            }
        }

        // And do it all over again for the side...

        // Find start of scalable region.
        int sideScalableStart = 0;
        while (true) {
            if (pixels[sideScalableStart][0] == ARGB_BLACK) {
                break;
            }

            sideScalableStart++;
        }

        // Find the end...
        int sideScalableEnd = sideScalableStart + 1;
        while (sideScalableEnd < pixels.length) {
            if (pixels[sideScalableEnd][0] != ARGB_BLACK) {
                break;
            }

            sideScalableEnd++;
        }

        int yCollapsed = 0;
        // If it's a one pixel region, there is nothing to do.
        if (sideScalableEnd != sideScalableStart + 1) {

            // Scan all rows from here to ensure they're all the same.
            if (rowRangeMatches(pixels, sideScalableStart, sideScalableEnd)) {
                // Hooray, it turns out that the detected region can safely be collapsed.
                collapseRowRange(pixels, sideScalableStart, sideScalableEnd);
                yCollapsed = sideScalableEnd - sideScalableStart - 1;
            } else {
                System.err.println("Row range doesn't match: ignoring!");
            }
        }

        int width = pixels[0].length - xCollapsed;
        int height = pixels.length - yCollapsed;

        return new Point(width, height);
    }

    /**
     * Write given pixels to disk.
     */
    private static void writeImage(int[] pixels, int width, int height, String outfile) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);

        File outputfile = new File(outfile);
        try {
            ImageIO.write(image, "png", outputfile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Collapse the dead space out of the buffer and flatten it for writing...
     */
    private static int[] collapseAndFlattenBuffer(int[][] pixels, int width, int height) {
        int[] newBuffer = new int[width * height];
        for (int i = 0; i < height; i++) {
            System.arraycopy(pixels[i], 0, newBuffer, i * width, width);
        }

        return newBuffer;
    }

    /**
     * Collapse a column range from start to end to a single pixel wide column.
     */
    private static void collapseColumnRange(int[][] pixels, int start, int end) {
        for (int depth = 0; depth < pixels.length; depth++) {
            System.arraycopy(pixels[depth], end, pixels[depth], start + 1, pixels[depth].length - end);
        }
    }

    private static void collapseRowRange(int[][] pixels, int start, int end) {
        System.arraycopy(pixels, end, pixels, start + 1, pixels.length - end);
    }

    private static boolean columnRangeMatches(int[][] pixels, int start, int end) {
        // Run down the column checking each row as you go...
        for (int depth = 0; depth < pixels.length; depth++) {
            int targetPixel = pixels[depth][start];

            // Check everything on this row in the region matches that...
            for (int i = start + 1; i < end; i++) {
                if (pixels[depth][i] != targetPixel) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean rowRangeMatches(int[][] pixels, int start, int end) {
        // Run across the row checking each column as you go...
        for (int width = 0; width < pixels[0].length; width++) {
            int targetPixel = pixels[start][width];

            // Check everything on this column in the region matches that...
            for (int i = start + 1; i < end; i++) {
                if (pixels[i][width] != targetPixel) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Run along the top row (and down the first column) and ensure only one contiguous block of
     * black pixels exist.
     */
    public static boolean hasMultipleScalableRegions(int[][] pixels) {
        boolean seenTop = false;
        boolean seenEnd = false;

        for (int i = 0; i < pixels[0].length; i++) {
            boolean isBlack = pixels[0][i] == ARGB_BLACK;

            if (isBlack && !seenTop) {
                seenTop = true;
                continue;
            }

            if (!isBlack && seenTop) {
                seenEnd = true;
                continue;
            }

            if (isBlack && seenEnd) {
                return true;
            }
        }

        if (!seenTop) {
            System.err.println("No scalable region found on top...");
        }

        seenTop = false;
        seenEnd = false;
        for (int i = 0; i < pixels.length; i++) {
            boolean isBlack = pixels[i][0] == ARGB_BLACK;

            if (isBlack && !seenTop) {
                seenTop = true;
                continue;
            }

            if (!isBlack && seenTop) {
                seenEnd = true;
                continue;
            }

            if (isBlack && seenEnd) {
                return true;
            }
        }

        if (!seenTop) {
            System.err.println("No scalable region found on side...");
        }

        return false;
    }
}
