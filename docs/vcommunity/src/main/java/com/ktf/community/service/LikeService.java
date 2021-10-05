package com.ktf.community.service;

import com.ktf.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

/** 点赞相关
 * @author github.com/kuangtf
 * @date 2021/9/7 9:25
 */
@Service
public class LikeService {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     *
     * @param entityType
     * @param entityId
     * @return
     */
    public long findEntityLikeCount(int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        return redisTemplate.opsForSet().size(entityLikeKey);
    }

    /**
     * 查询某个用户获得赞数量
     * @param userId
     * @return
     */
    public int findUserLikeCount(int userId) {
        String userLikeKey = RedisKeyUtil.getUserLikeKey(userId);
        Integer count = (Integer) redisTemplate.opsForValue().get(userLikeKey);
        return count == null ? 0 : count;
    }

    /**
     * 查询某个用户对某个实体的点赞状态（是否已赞）
     * @param userId 用户 id
     * @param entityType 实体类型（用户，帖子，评论）
     * @param entityId 实体 Id
     * @return 1：已赞， 0：未赞
     */
    public int findEntityLikeStatus(int userId, int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        // 此处使用 redis 的 set，天然去重
        return redisTemplate.opsForSet().isMember(entityLikeKey, userId) ? 1 : 0;
    }

    /**
     * 点赞
     * @param userId 点赞用户的 id
     * @param entityType 被点赞的实体
     * @param entityId 实体 id
     * @param entityUserId 被赞的帖子或评论的作者 id
     */
    public void like(int userId, int entityType, int entityId, int entityUserId) {
        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations redisOperations) throws DataAccessException {
                String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
                String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);

                // 判断用户是否已经点过赞了
                Boolean isMember = redisOperations.opsForSet().isMember(entityLikeKey, userId);

                // 开启事务
                redisOperations.multi();

                if (isMember) {
                    // 如果用户已经点过赞，点第二次则取消点赞
                    redisOperations.opsForSet().remove(entityLikeKey, userId);
                    redisOperations.opsForValue().decrement(userLikeKey);
                }
                else {
                    redisTemplate.opsForSet().add(entityLikeKey, userId);
                    redisOperations.opsForValue().increment(userLikeKey);
                }

                // 提交事务
                return redisOperations.exec();
            }
        });
    }
}









