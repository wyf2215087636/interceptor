package com.wangyifei;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * @author WangYifei
 * @date 2021-06-07 16:25
 * @describe 动态规划 --》 二维装箱 获取 纸张 矩形排版最优答案
 */
public class Boxing {
    /**
     * 该方向 可以 排版 #{count} 个
     */
    private int count = 0;
    /**
     * 画图每一步的步骤，linkedlist 顺序保存
     */
    private final List<Optimal> optimals = new LinkedList<>();
    // 1080 780

    /**
     * 尝试 纸张 横向 和纵向 两种开始排版方向，获取最优
     * @param paperLength 纸张 长
     * @param paperWidth 纸张 宽
     * @param length 矩形 长
     * @param width 矩形 宽
     * @return 获取最优解的 纸张排版方式， 期间 存在 矩形 旋转 的情况
     * <p>Optimal{paperLength=780, paperWidth=1080, length=130, width=475, max=6}</p>
     * <p>第一步 --》 最优， 最优之后的纸张 长， 纸张宽， 矩形 长、矩形宽，该摆法，当前行需要摆放 6个</p>
     * <p>Optimal{paperLength=780, paperWidth=605, length=130, width=475, max=6}</p>
     * <p>第二步 --》 最优， 最优之后的纸张 长， 纸张宽， 矩形 长、矩形宽，该摆法，当前行需要摆放 6个</p>
     * <p>Optimal{paperLength=780, paperWidth=130, length=475, width=130, max=1}</p>
     * <p>第三步 --》 最优， 最优之后的纸张 长， 纸张宽， 矩形 长、矩形宽，该摆法，当前行需要摆放 1个</p>
     *
     * 共计 可以摆放 13 个
     */
    public static Boxing merge(int paperLength, int paperWidth, int length, int width) {
        Boxing lBoxing = new Boxing();
        lBoxing.count(paperLength,paperWidth,length,width);
        Boxing wBoxing = new Boxing();
        wBoxing.count(paperWidth,paperLength,length,width);
        if (lBoxing.getCount() > wBoxing.getCount()) {
            return lBoxing;
        } else {
            return wBoxing;
        }
    }

    /**
     * 排版开始之前 数据初始化，和算法调用
     * @param paperLength 纸张长
     * @param paperWidth 纸张宽
     * @param length 矩形长
     * @param width 矩形宽
     */
    private void count(Integer paperLength, Integer paperWidth, Integer length, Integer width) {
        Node node = new Node(paperLength, paperWidth, length, width);
        getNum(node);
    }

    /**
     * 最终算法 实现，获取 当前方向最优解 的摆放个数 和 步骤方案
     * @param node 封装的 排版源数据
     */
    private void getNum(Node node) {
        if (quantityCalculation(node)) {
            node.setMinus(node.getWidth());
        } else {
            node.setMinus(node.getLength());
        }
        int lastWidth = 0;
        node.setLastWidth(0);
        // 尝试 反转操作，如果反转成功继续执行
        if (( lastWidth = tryToReverse(node)) >= 0) {
            node.setLastWidth(lastWidth);
            // 获取总数
            Optimal optimal = null;
            // 如果减少的长度等于 长度 ，然后再一次验证 剩下的宽度 足以 支撑横着多还是竖着多
            // 如果 足以支撑 横着放 的数量 > 竖着放的数量，优先横着放
            if (node.getMinus() == node.getWidth()) {
                // 减数 等于 宽，那么count += lMax
                count += node.getlMax();
                optimal = new Optimal(node.getPaperLength(), node.getPaperWidth(), node.getLength(), node.getWidth(), node.getlMax());
            } else {
                // 减数 等于 长，那么count += wMax
                count += node.getwMax();
                optimal = new Optimal(node.getPaperLength(), node.getPaperWidth(), node.getWidth(), node.getLength(), node.getwMax());
            }
            optimals.add(optimal);
            // 最新的宽 赋值, 长不变
            node.setPaperWidth(lastWidth);
            // 说明还够长，递归
            getNum(node);
        }
        // 不够长了，返回count
    }

    private boolean quantityCalculation(Node node) {
        // 正常摆放 长 除以长  宽 除以宽
        BigDecimal llD = BigDecimal.valueOf(node.getPaperLength()).divide(BigDecimal.valueOf(node.getLength()), 0, RoundingMode.DOWN);
        BigDecimal wwD = BigDecimal.valueOf(node.getPaperWidth()).divide(BigDecimal.valueOf(node.getWidth()), 0, RoundingMode.DOWN);
        // 矩形旋转方向摆放
        BigDecimal lwD = BigDecimal.valueOf(node.getPaperLength()).divide(BigDecimal.valueOf(node.getWidth()), 0, RoundingMode.DOWN);
        BigDecimal wlD = BigDecimal.valueOf(node.getPaperWidth()).divide(BigDecimal.valueOf(node.getLength()), 0, RoundingMode.DOWN);

        // 结果相乘对比
        BigDecimal llDCount = llD.multiply(wwD);
        BigDecimal lwDCount = lwD.multiply(wlD);
        // 还得判断  node.getMinus() == node.getWidth() 是不是等于，如果不等于，但是
        if (llDCount.compareTo(lwDCount) >= 0) {
            // 反转
            return true;
        }
        return false;
    }

    /**
     * 矩形反转 操作，长 = 宽， 宽 = 长
     * @param node 操作的节点
     * @return 返回反转之后 剩余的宽
     */
    private int tryToReverse(Node node) {
        int lastWidth = 0;
        if (( lastWidth = node.getPaperWidth() - node.getMinus() ) >= 0) {
            return lastWidth;
        } else {
            // 不够减的，尝试反转
            if (node.getMinus() == node.getLength()) {
                node.setMinus(node.getWidth());
            } else {
                // minus = length;
                node.setMinus(node.getLength());
            }

            if (( lastWidth = node.getPaperWidth() - node.getMinus() ) >= 0) {
                return lastWidth;
            } else {
                return -1;
            }
        }
    }

    /**
     * 获取 长度
     * @return
     */
    public int getCount() {
        return count;
    }

    /**
     * 获取 最优
     * @return
     */
    public List<Optimal> getOptimals() {
        return optimals;
    }

    /**
     * 纸张 数据存储节点，主要作用，基本数据类型函数传递没有办法 改变其出栈之后的值
     * 利用 对象引用 变量，实时改变其值
     */
    private static class Node{
        /**
         * 纸张 长
         */
        private int paperLength;

        /**
         * 纸张宽
         */
        private int paperWidth;

        /**
         * 填充 矩形 长
         */
        private int length;

        /**
         * 填充 矩形 宽
         */
        private int width;

        /**
         * 纸张长 / 矩形长 的最大数量
         */
        private int lMax;

        /**
         * 纸张宽 / 矩形宽 的最大数量
         */
        private int wMax;

        /**
         * 每一步骤之后需要折减的纸张 宽度
         */
        private int minus;

        private int lastWidth;

        /**
         * 初始化 当前 方向的 初始化 数据
         * @param paperLength 纸张 长
         * @param paperWidth 纸张 宽
         * @param length 矩形长
         * @param width 矩形宽
         */
        Node(int paperLength, int paperWidth, int length, int width) {
            this.paperLength = paperLength;
            this.paperWidth = paperWidth;
            this.length = length;
            this.width = width;
            this.lMax = paperLength / length;
            this.wMax = paperLength / width;
            this.minus = 0;
        }

        public int getLastWidth() {
            return lastWidth;
        }

        public void setLastWidth(int lastWidth) {
            this.lastWidth = lastWidth;
        }

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

        public int getlMax() {
            return lMax;
        }

        public void setlMax(int lMax) {
            this.lMax = lMax;
        }

        public int getwMax() {
            return wMax;
        }

        public void setwMax(int wMax) {
            this.wMax = wMax;
        }

        public int getMinus() {
            return minus;
        }

        public void setMinus(int minus) {
            this.minus = minus;
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
    }
}
