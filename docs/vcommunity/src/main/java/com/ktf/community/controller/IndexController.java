package com.ktf.community.controller;

import com.ktf.community.entity.DiscussPost;
import com.ktf.community.entity.Page;
import com.ktf.community.entity.User;
import com.ktf.community.service.DiscussPostService;
import com.ktf.community.service.LikeService;
import com.ktf.community.service.UserService;
import com.ktf.community.util.CommunityConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**首页
 * @author github.com/kuangtf
 * @date 2021/9/5 17:57
 */
@Controller
public class IndexController implements CommunityConstant {

    @Autowired
    private UserService userService;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private LikeService likeService;

    /**
     * 要是后面没有加路基就转发到index页面
     * @return
     */
    @GetMapping("/")
    public String root() {
        return "forward:/index";
    }

    /**
     * 进入首页
     * @param model
     * @param page
     * @param orderMode
     * @return
     */
    @GetMapping("/index")
    public String getIndexPage(Model model, Page page, @RequestParam(name = "orderMode", defaultValue = "0") int orderMode) {
        // 获取总页数（userId = 0 时是总页数）
        page.setRows(discussPostService.findDiscussPostRows(0));
        page.setPath("/index?orderMode=" + orderMode);

        // 分页查询
        List<DiscussPost> list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit(), orderMode);

        // 封装帖子和该帖子对应的用户信息
        ArrayList<Map<String, Object>> discussPosts = new ArrayList<>();
        if (list != null) {
            for (DiscussPost post : list) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("post", post);
                User user = userService.findUserById(post.getUserId());
                map.put("user", user);
                long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                map.put("likeCount", likeCount);

                discussPosts.add(map);
            }
        }
        model.addAttribute("discussPosts", discussPosts);
        model.addAttribute("orderMode", orderMode);
        return "index";
    }

    /**
     * 进入 500 错误界面
     * @return
     */
    @GetMapping("/error")
    public String getErrorPage() {
        return "/error/500";
    }

    /**
     * 没有权限访问时的错误界面（也是 404）
     * @return
     */
    @GetMapping("/denied")
    public String getDeniedPage() {
        return "/error/404";
    }

}
