package com.xinyi.beehive.algo;

/**
 * 时间对齐算法
 *
 * @author 新一
 * @date 2025/1/4 13:56
 */
public class TimeAlignmentAlgo {

    /**
     * 根据时间间隔和对齐规则来计算即将到来的一个时间点
     *
     * @param intervalInSeconds 时间间隔（秒）
     * @return 下一个时间点的时间戳（毫秒）
     * @throws IllegalArgumentException 配置不匹配时抛出异常
     */
    public static long calculateNextTime(long intervalInSeconds) {
        // 获取当前时间
        long currentTimeMillis = System.currentTimeMillis() / 1000 * 1000;
        // 获取当前时间到下一个时间点的间隔时间
        long intervalTime = calculateIntervalTime(currentTimeMillis, intervalInSeconds);
        // 计算下一个时间点的时间戳
        return currentTimeMillis + intervalTime;
    }

    /**
     * 计算当前时间到下一个对齐时间点的间隔时间
     *
     * @param currentTimeMillis 当前时间戳（毫秒）
     * @param intervalInSeconds 时间间隔（秒）
     * @return 当前时间到下一个时间点的间隔时间（毫秒）
     * throws IllegalArgumentException 间隔时间小于等于 0 时抛出异常
     */
    public static long calculateIntervalTime(long currentTimeMillis, long intervalInSeconds) {
        if (intervalInSeconds <= 0) {
            throw new IllegalArgumentException("间隔时间必须大于 0，intervalInSeconds = " + intervalInSeconds);
        }
        if (intervalInSeconds % 86400 == 0) {
            // 天级对齐
            return calculateIntervalTime(currentTimeMillis, intervalInSeconds, 86400);
        } else if (intervalInSeconds % 3600 == 0) {
            // 小时级对齐
            return calculateIntervalTime(currentTimeMillis, intervalInSeconds, 3600);
        } else if (intervalInSeconds % 60 == 0) {
            // 分钟级对齐
            return calculateIntervalTime(currentTimeMillis, intervalInSeconds, 60);
        } else {
            // 秒级对齐
            return calculateIntervalTime(currentTimeMillis, intervalInSeconds, 1);
        }
    }

    /**
     * 通用对齐计算逻辑，根据当前时间和对齐单位计算其中的空格时间
     *
     * @param currentTimeMillis 当前时间戳（毫秒）
     * @param intervalSeconds 配置的时间间隔（秒）
     * @param unitSeconds 对齐单位时间（秒），如 1 秒、60 秒、3600 秒
     * @return 当前时间到下一个时间点的间隔时间（单位：毫秒），确保非负值
     */
    private static long calculateIntervalTime(long currentTimeMillis, long intervalSeconds, long unitSeconds) {
        // 将当前时间转换为对齐单位的时间块（如秒块、分钟块）
        long currentUnit = currentTimeMillis / 1000 / unitSeconds;

        // 计算时间间隔对应的对齐单位数量
        long intervalUnits = intervalSeconds / unitSeconds;

        // 计算下一个对齐的单位时间块（如下一个秒块、分钟块、小时块）
        long nextAlignedUnit = ((currentUnit / intervalUnits) + 1) * intervalUnits;

        // 转换为毫秒级时间戳并计算延迟时间
        long nextTriggerTimeMillis = nextAlignedUnit * unitSeconds * 1000;

        // 确保延迟时间非负
        return Math.max(0, nextTriggerTimeMillis - currentTimeMillis);
    }
}