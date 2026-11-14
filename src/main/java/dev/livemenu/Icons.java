package dev.livemenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Pixel-art icons (from the supplied sheet), drawn with filled rectangles so no textures are needed. */
public final class Icons {
    private Icons() {}

    public static final String[] FRIENDS = {
            "......###...",
            "...###.###..",
            "..#####.##..",
            "..#####.##..",
            "..#####.#...",
            "...###......",
            "........##..",
            "..######.##.",
            ".########.##",
            "##########.#",
            "##########.."
    };
    public static final String[] GEAR = {
            "....###....",
            ".##.###.##.",
            ".#########.",
            "..###.###..",
            "####...####",
            "###.....###",
            "####...####",
            "..###.###..",
            ".#########.",
            ".##.###.##.",
            "....###...."
    };
    public static final String[] MENU = {
            "#########",
            ".........",
            ".........",
            "#########",
            ".........",
            ".........",
            "#########"
    };
    public static final String[] EXIT = {
            "#####....",
            "##.......",
            "#........",
            "#.....#..",
            "#.....##.",
            "#.#######",
            "#.....##.",
            "#.....#..",
            "#........",
            "##.......",
            "#####...."
    };

    public static int w(String[] icon, int p) { return icon[0].length() * p; }
    public static int h(String[] icon, int p) { return icon.length * p; }

    public static void draw(GuiGraphicsExtractor g, String[] icon, int x, int y, int p, int color) {
        for (int r = 0; r < icon.length; r++) {
            String row = icon[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) == '#') {
                    int start = c;
                    while (c < row.length() && row.charAt(c) == '#') c++;
                    g.fill(x + start * p, y + r * p, x + c * p, y + (r + 1) * p, color);
                } else {
                    c++;
                }
            }
        }
    }
}
