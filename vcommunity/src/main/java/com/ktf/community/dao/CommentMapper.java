package com.ktf.community.dao;

import com.ktf.community.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author github.com/kuangtf
 * @date 2021/9/7 15:38
 */
@Repository
@Mapper
public interface CommentMapper {

    /**
     * 查询某个用户的评论 / 回复数量
     * @param userId
     * @return
     */
    int selectCommentCountByUserId(int userId);

    /**
     * 分页查询某个用户的评论/回复列表
     * @param userId
     * @param offset
     * @param limit
     * @return
     */
    List<Comment> selectCommentByUserId(int userId, int offset, int limit);

    /**
     * 根据 id 查询评论
     * @param id
     * @return
     */
    Comment selectCommentById(int id);

    /**
     * 根据评论目标（类别、id）对评论进行分页查询
     * @param entityType 评论目标的类别（帖子、回复）
     * @param entityId 评论目标的 id
     * @param offset 每页的起始索引
     * @param limit 每页显示多少条数据
     * @return 所以评论
     */
    List<Comment> selectCommentByEntity(int entityType, int entityId, int offset, int limit);

    /**
     * 查询评论数量
     * @param entityType
     * @param entityId
     * @return
     */
    int selectCountByEntity(int entityType, int entityId);

    /**
     * 添加评论
     * @param comment
     * @return
     */
    int insertComment(Comment comment);
}












