package houmio;

public class Scale {
    public static int scale(int sMin, int sMax, int dMin, int dMax, int x) {
        return (int)((float)(dMax - dMin)/(float)(sMax - sMin)*(x - sMin) + dMin);
    }
}
