package com.ktf.community.dao.elasticsearch;

import com.ktf.community.entity.DiscussPost;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * @author github.com/kuangtf
 * @date 2021/9/9 13:30
 */
@Repository
public interface DiscussPostRepository extends ElasticsearchRepository<DiscussPost, Integer> {

}
