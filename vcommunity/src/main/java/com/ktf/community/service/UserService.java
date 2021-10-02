package com.ktf.community.service;

import com.ktf.community.dao.UserMapper;
import com.ktf.community.entity.LoginTicket;
import com.ktf.community.entity.User;
import com.ktf.community.util.CommunityConstant;
import com.ktf.community.util.CommunityUtil;
import com.ktf.community.util.MailClient;
import com.ktf.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author github.com/kuangtf
 * @date 2021/9/5 12:37
 */
@Service
public class UserService implements CommunityConstant {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private MailClient mailClient;

    @Autowired
    private RedisTemplate redisTemplate;

    // 网站域名，配置文件中可配
    @Value("${community.path.domain}")
    private String domain;

    // 项目名（访问路径），配置文件中可配
    @Value("${server.servlet.context-path}")
    private String contextPath;



    /**
     *  用户注册
     * @param user controller 中传过来的值
     * @return  Map<String, Object> 返回错误提示消息，如果返回的 map 为空，则说明注册成功
     */
    public Map<String, Object> register(User user) {
        Map<String, Object> map = new HashMap<>();

        // 判断用户填入的账号密码等是否合法，如果不合法，将错误消息返回给 controller 再显示给用户具体信息
        if (user == null) {
            throw  new IllegalArgumentException("参数不能为空");
        }

        if (StringUtils.isBlank(user.getUsername())) {
            map.put("usernameMsg", "账号不能为空");
            return map;
        }

        if (StringUtils.isBlank(user.getPassword())) {
            map.put("passwordMsg", "密码不能为空");
            return map;
        }

        if (StringUtils.isBlank(user.getEmail())) {
            map.put("emailMsg", "邮箱不能为空");
            return map;
        }

        // 验证账号是否已存在，根据用户名从数据库中查询是否已经存在该用户，进行逻辑判断
        User u = userMapper.selectByName(user.getUsername());
        if (u != null) {
            map.put("usernameMsg", "该账号已存在");
            return map;
        }

        // 验证邮箱是否已存在
        u = userMapper.selectByEmail(user.getEmail());
        if (u != null) {
            map.put("emailMsg", "该邮箱已被注册");
            return  map;
        }

        // 如果用户填入的信息没有问题，就注册用户
        // salt：随机字符串的前 5 个字符串作为盐
        user.setSalt(CommunityUtil.generateUUID().substring(0, 5));
        // 加盐加密：就是将用户输入的密码和生成的盐进行 md5 加密，使用的是 spring 中的 md5
        user.setPassword(CommunityUtil.md5(user.getPassword() + user.getSalt()));
        // 默认设置为普通用户
        user.setType(0);
        // 默认是未激活状态
        user.setStatus(0);
        // 设置激活码：就是随机生成的字符串
        user.setActivationCode(CommunityUtil.generateUUID());
        // 随机头像（用户登录之后才可以查看，使用的是牛客网的图库, 后面那个随机字符数0~999是图库的格式）
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        // 注册时间
        user.setCreateTime(new Date());
        userMapper.insertUser(user);

        // 给注册用户发送激活邮箱
        // 就是一个键值对，用来存储东西的
        Context context = new Context();
        context.setVariable("email", user.getEmail());
        // 激活链接：http://localhost:8080/community/activation/用户id/激活码
        String url = domain + contextPath + "/activation/" + user.getId() + "/" + user.getActivationCode();
        context.setVariable("url", url);
        // 猜想：将 context 中的变量渲染到前一个参数指定的页面中，并且将这个页面转化成一个字符串
        // 然后将这个字符串通过邮箱发送给用户，用户即可查看此页面
        String content = templateEngine.process("/mail/activation", context);
        // 将激活链接通过邮箱发送给用户，用户点击该链接即可激活
        mailClient.sendMail(user.getEmail(), "激活微社区账号", content);

        return map;
    }

    /**
     * 激活用户
     * @param userId  用户 id
     * @param code  controller 传过来的激活码
     * @return 激活状态
     */
    public int activation(int userId, String code) {
        User user = userMapper.selectById(userId);
        // 根据激活链接里出来的用户 id 查看数据库中该用户的激活状态
        // 如果用户状态为 1， 表示已激活
        if (user.getStatus() == 1) {
            // 用户已激活，返回 1
            return ACTIVATION_REPEAT;
        }
        // 比较前端传过来的激活码和创建用户是生成的激活码（已存入数据库中的）是否一样
        else if (user.getActivationCode().equals(code)) {
            // 修改用户状态为已激活
            userMapper.updateStatus(userId, 1);
            // 用户信息变更，清除缓存中的旧值
            clearCache(userId);
            // 用户激活成功，返回 0
            return ACTIVATION_SUCCESS;
        }
        else {
            // 用户激活失败，返回 2
            return ACTIVATION_FAILURE;
        }
    }

    /**
     * 用户信息变更时清除对应缓存数据
     * @param userId
     */
    private void clearCache(int userId) {
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(redisKey);
    }

    /**
     * 用户登录 （为用户创建凭证）
     * @param username
     * @param password
     * @param expiredSeconds  多少秒后凭证过期
     * @return Map<String, Object> 返回错误提示消息以及 ticket(凭证)
     */
    public Map<String, Object> login(String username, String password, int expiredSeconds) {

        HashMap<String, Object> map = new HashMap<>();

        // 空值处理
        if (StringUtils.isBlank(username)) {
            map.put("usernameMsg", "账号不能为空");
            return map;
        }
        if (StringUtils.isBlank(password)) {
            map.put("passwordMsg", "密码不能为空");
            return map;
        }

        // 验证账号
        User user = userMapper.selectByName(username);
        if (user == null) {
            map.put("usernameMsg", "该账号不存在");
            return map;
        }

        // 验证状态
        if (user.getStatus() == 0) {
            // 账号未激活
            map.put("usernameMsg", "该账号未激活");
            return map;
        }

        // 验证密码
        password = CommunityUtil.md5(password + user.getSalt());
        if (!user.getPassword().equals(password)) {
            map.put("passwordMsg", "密码错误");
            return map;
        }

        // 用户名和密码正确，为该用户生成登录凭证
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(user.getId());
        // 生成凭证，就是一个随机字符串
        loginTicket.setTicket(CommunityUtil.generateUUID());
        // 设置凭证状态为有效（当用户登出的时候，设置凭证状态为无效））0：有效，1：无效
        loginTicket.setStatus(0);
        // 设置凭证存活时间：这里 Data 需要传入的时间单位是毫秒，而 expiredSeconds 的单位是秒，需要 * 1000
        loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds * 1000));

        // 将登录凭证对象存入 redis
        String redisKey = RedisKeyUtil.getTicketKey(loginTicket.getTicket());
        // key = 登录凭证 trick 的 redis 格式，value = 登录凭证的对象
        redisTemplate.opsForValue().set(redisKey, loginTicket);

        // controller 层需要返回值判断登录状态
        map.put("ticket", loginTicket.getTicket());

        return map;
    }

    /**
     * 用户登出（将凭证状态设置为无效）
     * @param ticket
     */
    public void logout(String ticket) {
        // 修改（先删除再插入）对应用户在 redis 中的凭证状态
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        // 从 redis 获取当前用户的登录凭证
        LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(redisKey);
        // 设置凭证状态为无效（1是无效，0是有效）
        loginTicket.setStatus(1);
        // 将更新状态后的凭证存入 redis
        redisTemplate.opsForValue().set(redisKey, loginTicket);
    }

    /**
     * 重置密码
     * @param account  账户名，目前是用户名
     * @param password 密码
     * @return Map<String, Object> 返回错误提示消息，如果返回的 map 为空，则说明发送验证码成功
     */
    public Map<String, Object> doResetPwd(String account, String password) {
        HashMap<String, Object> map = new HashMap<>(2);
        // 判空
        if (StringUtils.isBlank(password)) {
            map.put("errMsg", "密码不能为空");
            return map;
        }
        User user = userMapper.selectByName(account);
        if (user == null) {
            map.put("errMsg", "未发现账号");
            return map;
        }
        String passwordEncode = CommunityUtil.md5(password + user.getSalt());
        int i = userMapper.updatePassword(user.getId(), passwordEncode);
        if (i <= 0) {
            map.put("errMsg", "修改数据库密码错误");
        } else {
            // 如果修改成功，这清除缓存中的用户信息
            clearCache(user.getId());
        }
        return map;
    }

    /**
     * 发送邮箱验证码（用于忘记密码）
     * @param account  账户名, 目前是用户名
     * @return Map<String, Object> 返回错误提示消息，如果返回的 map 为空，则说明发送验证码成功
     */
    public Map<String, Object> doSendEmailCode4ResetPwd(String account) {
        HashMap<String, Object> map = new HashMap<>(2);
        User user = userMapper.selectByName(account);
        if (user == null) {
            map.put("errMsg","未发现账号");
            return map;
        }
        String email = user.getEmail();
        if (StringUtils.isBlank(email)) {
            map.put("errMsg", "该账号未绑定邮箱");
            return map;
        }

        // 生成 6 位验证码
        String randomCode = CommunityUtil.getRandomCode(6);
        // 给注册用户发送激活邮件
        Context context = new Context();
        context.setVariable("email", email);
        context.setVariable("randomCode", randomCode);
        String content = templateEngine.process("/mail/forget", context);
        mailClient.sendMail(email, "重置微社区账号密码", content);
        String redisKey = "EmailCode4ResetPwd" + account;
        // 设有验证码有效时间 10 分钟
        redisTemplate.opsForValue().set(redisKey, randomCode, 600, TimeUnit.SECONDS);
        return map;
    }

    /**
     * 根据 ticket 查询 LoginTicket 信息
     * @param ticket
     * @return
     */
    public LoginTicket findLoginTicket(String ticket) {
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        return (LoginTicket) redisTemplate.opsForValue().get(redisKey);
    }

    /**
     * 根据 id 查询用户
     * @param id
     * @return
     */
    public User findUserById(int id) {
        // return userMapper.selectById(id);  // 如果频繁去查数据库，效率比较低
        User user = getCache(id);
        if (user == null) {
            user = initCache(id);
        }
        return user;
    }

    /**
     * 缓存中没有该用户信息时，则将其存入缓存
     * @param userId
     * @return
     */
    private User initCache(int userId) {
        User user = userMapper.selectById(userId);
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.opsForValue().set(redisKey, user, 3600, TimeUnit.SECONDS);
        return user;
    }

    /**
     * 优先从缓存中取值
     * @param userId
     * @return
     */
    private User getCache(int userId) {
        String redisKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(redisKey);
    }

    /**
     * 获取某个用户的权限
     * @param userId
     * @return
     */
    public Collection<? extends GrantedAuthority> getAuthorities(int userId) {
        User user = this.findUserById(userId);
        ArrayList<GrantedAuthority> list = new ArrayList<>();
        list.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                switch (user.getType()) {
                    case 1:
                        return AUTHORITY_ADMIN;
                    case 2:
                        return AUTHORITY_MODERATOR;
                    default:
                        return AUTHORITY_USER;
                }
            }
        });
        return list;
    }

    /**
     * 修改用户头像
     * @param userId
     * @param headUrl
     * @return
     */
    public int updateHeader(int userId, String headUrl) {
        int rows = userMapper.updateHeader(userId, headUrl);
        // 清除缓存中的用户
        clearCache(userId);
        return rows;
    }

    /**
     * 修改用户密码（对新密码加盐存入数据库）
     * @param userId
     * @param newPassword
     * @return
     */
    public int updatePassword(int userId, String newPassword) {
        User user = userMapper.selectById(userId);
        // 重新加盐加密
        newPassword = CommunityUtil.md5(newPassword + user.getSalt());
        clearCache(userId);
        return userMapper.updatePassword(userId, newPassword);
    }

    /**
     * 根据 username 查询用户
     * @param username
     * @return
     */
    public User findUserByName(String username) {
        return userMapper.selectByName(username);
    }


}











