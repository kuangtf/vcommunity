package com.ktf.community.controller;

import com.ktf.community.entity.Comment;
import com.ktf.community.entity.DiscussPost;
import com.ktf.community.entity.Page;
import com.ktf.community.entity.User;
import com.ktf.community.service.*;
import com.ktf.community.util.CommunityConstant;
import com.ktf.community.util.CommunityUtil;
import com.ktf.community.util.HostHolder;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**用户相关
 * @author github.com/kuangtf
 * @date 2021/9/7 10:16
 */
@Controller
@RequestMapping("/user")
public class UserController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${qiniu.key.access}")
    private String accessKey;

    @Value("${qiniu.key.secret}")
    private String secretKey;

    @Value("${qiniu.bucket.header.name}")
    private String headerBucketName;

    @Value("${qiniu.bucket.header.url}")
    private String headerBucketUrl;

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FollowService followService;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private CommentService commentService;

    /**
     * 跳转至账号设置界面
     * @param model
     * @return
     */
    @GetMapping("/setting")
    public String getSettingPage(Model model) {
        // 生成上传文件的名称
        String fineName = CommunityUtil.generateUUID();
        model.addAttribute("fileName", fineName);

        // 设置响应信息（ qiniu 的规定写法）
        StringMap policy = new StringMap();
        policy.put("returnBody", CommunityUtil.getJSONString(0));

        // 生成上传到 qiniu 的凭证（ qiniu 的规定写法）
        Auth auth = Auth.create(accessKey, secretKey);
        String uploadToken = auth.uploadToken(headerBucketName, fineName, 3600, policy);
        model.addAttribute("uploadToken", uploadToken);

        return "/site/setting";
    }

    /**
     * 更新图像路径（将本地的图像路径更新为云服务器上的图形路径）
     * @param fileName 图片名称
     * @return
     */
    @PostMapping("/header/url")
    @ResponseBody
    public String updateHeaderUrl(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return CommunityUtil.getJSONString(1, "文件名不能为空");
        }

        // 文件在云服务器上的访问路径
        String url = headerBucketUrl + "/" + fileName;
        userService.updateHeader(hostHolder.getUser().getId(), url);

        return CommunityUtil.getJSONString(0);
    }

    /**
     * 修改用户密码
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @param model
     * @return
     */
    @PostMapping("/password")
    public String updatePassword(String oldPassword, String newPassword, Model model) {
        // 验证原密码是否正确
        User user = hostHolder.getUser();
        String md5OldPassword = CommunityUtil.md5(oldPassword + user.getSalt());
        if (!user.getPassword().equals(md5OldPassword)) {
            model.addAttribute("oldPasswordError", "原密码错误");
            return "/site/setting";
        }

        // 判断新密码是否合法
        String md5NewPassword = CommunityUtil.md5(newPassword + user.getSalt());
        if (user.getPassword().equals(md5NewPassword)) {
            model.addAttribute("newPasswordError", "新密码和原密码相同");
            return "/site/setting";
        }

        // 修改用户密码
        userService.updatePassword(user.getId(), newPassword);
        return "redirect:/index";
    }

    /**
     * 进入个人主页
     * @param userId 可以进入任意用户的主页
     * @param model
     * @return
     */
    @GetMapping("/profile/{userId}")
    public String getProfilePage(@PathVariable("userId") int userId, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在");
        }

        // 用户
        model.addAttribute("user", user);
        // 获赞数量
        int userLikeCount = likeService.findUserLikeCount(userId);
        model.addAttribute("userLikeCount", userLikeCount);
        // 关注数量
        long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        model.addAttribute("followeeCount", followeeCount);
        // 粉丝数量
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        model.addAttribute("followerCount", followerCount);
        // 当前登录用户是否已关注该用户,这个是进入别人的主页时，看是否显示关注按钮
        boolean hasFollowed = false;
        if (hostHolder.getUser() != null) {
            hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
        }
        model.addAttribute("hasFollowed", hasFollowed);
        // 该字段用于指示标签栏高亮
        model.addAttribute("tab", "profile");
        return "/site/profile";
    }

    /**
     * 进入我的帖子（查询某个用户的帖子列表）
     * @param userId
     * @param page
     * @param model
     * @return
     */
    @GetMapping("/discuss/{userId}")
    public String getMyDiscussPosts(@PathVariable("userId") int userId, Page page, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在");
        }
        model.addAttribute("user", user);

        // 该用户的帖子总数
        int rows = discussPostService.findDiscussPostRows(userId);
        model.addAttribute("rows", rows);

        page.setLimit(5);
        page.setPath("/user/discuss/" + userId);
        page.setRows(rows);

        // 分页查询（按照最新查询）
        List<DiscussPost> list = discussPostService.findDiscussPosts(userId, page.getOffset(), page.getLimit(), 0);
        // 封装帖子和该帖子对应的用户信息
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if(list != null) {
            for (DiscussPost post : list) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("post", post);
                long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                map.put("likeCount", likeCount);

                discussPosts.add(map);
            }
        }
        model.addAttribute("discussPosts", discussPosts);
        // 该字段用于指示标签栏高亮
        model.addAttribute("tab", "mypost");

        return "/site/my-post";
    }

    /**
     * 进入我的评论/回复（查询某个用户的评论/回复列表）
     * @param userId
     * @param page
     * @param model
     * @return
     */
    @GetMapping("/comment/{userId}")
    public String getMyComment(@PathVariable("userId") int userId,Page page, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在");
        }
        model.addAttribute("user", user);

        // 用户的评论/回复总数
        int commentCounts = commentService.findCommentCountByUserId(userId);
        model.addAttribute("commentCounts", commentCounts);

        page.setLimit(5);
        page.setPath("/user/comment/" + userId);
        page.setRows(commentCounts);

        // 分页查询
        List<Comment> list = commentService.findCommentByUserId(userId, page.getOffset(), page.getLimit());
        // 封装评论和该评论对应的帖子的信息
        ArrayList<Map<String, Object>> comments = new ArrayList<>();
        if (list != null) {
            for (Comment comment : list) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("comment", comment);
                // 显示评论/回复对应的文章信息
                if (comment.getEntityType() == ENTITY_TYPE_POST) {
                    // 如果是对帖子的评论，则直接查询 target_id 即可
                    DiscussPost post = discussPostService.findDiscussPostById(comment.getEntityId());
                    map.put("post", post);
                }
                else if (comment.getEntityType() == ENTITY_TYPE_COMMENT) {
                    // 如果是对评论的回复，则先根据该回复的 target_id 查询评论的 id，再根据该评论的 target_id 查询帖子的 id
                    Comment targetComment = commentService.findCommentById(comment.getEntityId());
                    DiscussPost post = discussPostService.findDiscussPostById(targetComment.getEntityId());
                    map.put("post", post);
                }
                comments.add(map);
            }
        }
        model.addAttribute("comments", comments);
        // 该字段用于指示标签栏高亮
        model.addAttribute("tab", "myreply");

        return "/site/my-reply";
    }
}



















