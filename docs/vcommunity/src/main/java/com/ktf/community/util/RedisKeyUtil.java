package com.ktf.community.util;

/**
 * 生成 Redis 的 key ：将存入 Redis 中的 key 设置成需求的样子
 *
 * @author github.com/kuangtf
 * @date 2021/9/5 8:40
 */
public class RedisKeyUtil {

    private static final String SPLIT = ":";
    private static final String PREFIX_ENTITY_LIKE = "like:entity"; // 实体的获赞
    private static final String PREFIX_USER_LIKE = "like:user"; // 用户的获赞
    private static final String PREFIX_FOLLOWER = "follower"; // 被关注（粉丝）
    private static final String PREFIX_FOLLOWEE = "followee"; // 关注的目标
    private static final String PREFIX_CAPTCHA = "captcha"; // 验证码
    private static final String PREFIX_TICKET = "ticket"; // 登录凭证
    private static final String PREFIX_USER = "user"; // 登录凭证
    private static final String PREFIX_UV = "uv"; // 独立访客
    private static final String PREFIX_DAU = "dau"; // 日活跃用户
    private static final String PREFIX_POST = "post"; // 用于统计帖子分数

    /**
     *  某个实体（帖子、评论、回复）的获赞
     *  key: like:entity:entityType:entityId, value: 点赞用户的 id
     *  如：用户 A （id = 11）给用户 B 的帖子（entityType = 1, entityId = 246）点赞后，存入 Redis 的信息
     *  key = like:entity:1:246, value = 11
     * @param entityType
     * @param entityId
     * @return redis 中的 key
     */
    public static String getEntityLikeKey(int entityType, int entityId) {
        return PREFIX_ENTITY_LIKE + SPLIT + entityType + SPLIT + entityId;
    }

    /**
     *  某个用户的获赞数量
     *  like:user:userId -> int
     *  如：id = 22 的用户获得了10个获赞
     *  key = like:user:22, value = 10
     * @param userId 获赞用户的 id
     * @return redis 中的 key
     */
    public static String getUserLikeKey(int userId) {
        return PREFIX_USER_LIKE + SPLIT + userId;
    }

    /**
     * 某个用户关注的实体
     * followee:userId:entityType -> zset(entityId, now) 以当前关注的时间进行排序
     * 如：id = 111 的用户关注了实体类型为3（用户），是实体的 id 是20，时间是 2021-09-05-xxxx
     * 存入 Redis：key = followee:111:3, value = (20, 2021-09-05-xxxx)
     * @param userId 用户 id
     * @param entityType  关注的实体类型：可以是用户、帖子、评论
     * @return redis 中的 key
     */
    public static String getFolloweeKey(int userId, int entityType) {
        return PREFIX_FOLLOWEE + SPLIT + userId + SPLIT + entityType;
    }

    /**
     * 某个实体拥有的粉丝（用户）
     * follower:entityType:entityId -> zset(userId, now)
     * 如：类型为3（用户）且 id 为 200 的实体，拥有一个id=11的粉丝，这个粉丝关注的时间是 2021-09-05-xxxx
     * 存入 Redis：key = follower:3:200, value = (11, 2021-09-05-xxxx)
     * @param entityType
     * @param entityId
     * @return redis 中的 key
     */
    public static String getFollowerKey(int entityType, int entityId) {
        return PREFIX_FOLLOWER + SPLIT + entityType + SPLIT + entityId;
    }

    /**
     * 登录验证码（指定这个验证码是针对哪个用户的）
     * @param owner 用户进入登录页面的时候，由于此时用户还未登录，无法通过 id 标识用户
     *              随机生成一个字符串，短暂的存入 cookie, 使用这个字符串来标识这个用户
     * @return redis 中的 key
     */
    public static String getCaptchaKey(String owner) {
        return PREFIX_CAPTCHA + SPLIT + owner;
    }

    /**
     * 登录凭证
     * @param ticket
     * @return redis 中的 key
     */
    public static String getTicketKey(String ticket) {
        return PREFIX_TICKET + SPLIT + ticket;
    }

    /**
     * 用户信息
     * @param userId
     * @return redis 中的 key
     */
    public static String getUserKey(int userId) {
        return PREFIX_USER + SPLIT + userId;
    }

    /**
     * 单日 UV
     * @param data
     * @return redis 中的 key
     */
    public static String getUVKey(String data) {
        return PREFIX_UV + SPLIT + data;
    }

    /**
     * 区间 UV
     * @param startDate
     * @param endDate
     * @return redis 中的 key
     */
    public static String getUVKey(String startDate, String endDate) {
        return PREFIX_UV + SPLIT + startDate + SPLIT + endDate;
    }

    /**
     * 单日 DAU
     * @param date
     * @return redis 中的 key
     */
    public static String getDAUKey(String date) {
        return PREFIX_DAU + SPLIT + date;
    }

    /**
     * 区间 DAU
     * @param startDate
     * @param endDate
     * @return redis 中的 key
     */
    public static String getDAUKey(String startDate, String endDate) {
        return PREFIX_DAU + SPLIT + startDate + SPLIT + endDate;
    }

    /**
     * 帖子分数
     * @return redis 中的 key
     */
    public static String  getPostScoreKey() {
        return PREFIX_POST + SPLIT + "score";
    }
}


















