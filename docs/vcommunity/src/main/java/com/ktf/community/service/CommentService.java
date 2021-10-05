package com.ktf.community.service;

import com.ktf.community.dao.CommentMapper;
import com.ktf.community.entity.Comment;
import com.ktf.community.util.CommunityConstant;
import com.ktf.community.util.SensitiveFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * @author github.com/kuangtf
 * @date 2021/9/7 15:27
 */
@Service
public class CommentService implements CommunityConstant {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Autowired
    private DiscussPostService discussPostService;


    /**
     * 查询某个用户的评论/回复数量
     * @param userId
     * @return
     */
    public int findCommentCountByUserId(int userId) {
        return commentMapper.selectCommentCountByUserId(userId);
    }

    /**
     * 分页查询某个用户的评论/回复列表
     * @param userId
     * @param offset
     * @param limit
     * @return
     */
    public List<Comment> findCommentByUserId(int userId, int offset, int limit) {
        return commentMapper.selectCommentByUserId(userId, offset, limit);
    }

    /**
     * 根据 id 查询评论
     * @param id
     * @return
     */
    public Comment findCommentById(int id) {
        return commentMapper.selectCommentById(id);
    }

    /**
     * 根据评论目标（类别、id）对评论进行分页查询
     * @param entityType
     * @param entityId
     * @param offset
     * @param limit
     * @return
     */
    public List<Comment> findCommentByEntity(int entityType, int entityId, int offset, int limit) {
        return commentMapper.selectCommentByEntity(entityType, entityId, offset, limit);
    }

    /**
     * 查询评论数量
     * @param entityType
     * @param entityId
     * @return
     */
    public int findCommentCount(int entityType, int entityId) {
        return commentMapper.selectCountByEntity(entityType, entityId);
    }

    /**
     * 添加评论（需要事务管理）：1、增加评论数据（向评论表中添加记录），2、修改帖子的评论数量（帖子表中的 comment_count）。
     *                      这两个操作要么一起成功，要么失败，不然可能评论增加了，数量没有增加
     * @param comment
     * @return
     *
     * 事务交给 spring 管理：isolation（隔离级别）：读已提交，propagation(事务传播)：
     * REQUIRED：如果当前存在事务，则加入该事务；如果当前没有事务，则创建一个新的事务
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public int addComment(Comment comment) {

        if (comment == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        // HTML 标签转义
        comment.setContent(HtmlUtils.htmlEscape(comment.getContent()));
        // 敏感词过滤
        comment.setContent(sensitiveFilter.filter(comment.getContent()));

        // 添加评论
        int rows = commentMapper.insertComment(comment);

        // 更新帖子的评论数量，和上面的操作要放到同一个事务中
        if (comment.getEntityType() == ENTITY_TYPE_POST) {
            int count = commentMapper.selectCountByEntity(comment.getEntityType(), comment.getEntityId());
            discussPostService.updateCommentCount(comment.getEntityId(), count);
        }

        return rows;
    }
}














