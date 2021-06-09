package com.wangyifei;

/**
 * @author WangYifei
 * @date 2021-06-08 9:01
 * @describe 排版 最优解 步骤封装
 */
public class Optimal {

    /**
     * 该最优解 纸张长
     */
    private int paperLength;

    /**
     * 该最优解 纸张宽
     */
    private int paperWidth;

    /**
     * 该最优解 矩形当前摆放方向时的长
     */
    private int length;

    /**
     * 该最优解 矩形当前摆放方向时的宽
     */
    private int width;

    /**
     * 该最优解 矩形当前摆放方向，横向从左 -》 右，依次放 max 个
     */
    private final int max;

    public int getPaperLength() {
        return paperLength;
    }

    public void setPaperLength(int paperLength) {
        this.paperLength = paperLength;
    }

    public int getPaperWidth() {
        return paperWidth;
    }

    public void setPaperWidth(int paperWidth) {
        this.paperWidth = paperWidth;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public Optimal(int paperLength, int paperWidth, int length, int width, int max) {
        this.paperLength = paperLength;
        this.paperWidth = paperWidth;
        this.length = length;
        this.width = width;
        this.max = max;
    }

    @Override
    public String toString() {
        return "Optimal{" +
                "paperLength=" + paperLength +
                ", paperWidth=" + paperWidth +
                ", length=" + length +
                ", width=" + width +
                ", max=" + max +
                '}';
    }
}
