package com.ktf.community.controller;

import com.google.code.kaptcha.Producer;
import com.ktf.community.entity.User;
import com.ktf.community.service.UserService;
import com.ktf.community.util.CommunityConstant;
import com.ktf.community.util.CommunityUtil;
import com.ktf.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录、登出、注册
 * 前端得到后端的值：通过后端传入的 model（数据模型），使用 ${变量} 即可获得
 * 后端得到前端的值：通过前端 input 的输入，要是后端参数和前端的一样，就可以直接获取，不然要加 @RequestParam(name="xxx")
 * @author github.com/kuangtf
 * @date 2021/9/5 12:26
 */
@Controller
public class LoginController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private Producer captchaProducer;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 进入注册界面
     */
    @GetMapping("/register")
    public String getRegisterPage() {
        return "site/register";
    }

    /**
     * 进入登录界面
     * @return
     */
    @GetMapping("/login")
    public String getLoginPage() {
        return "site/login";
    }

    /**
     * 进入重置密码界面
     * @return
     */
    @GetMapping("/resetPwd")
    public String getResetPwdPage() {
        return "site/reset-pwd";
    }

    /**
     * 注册用户
     * @param model
     * @param user
     * @return
     */
    @PostMapping("/register")
    public String register(Model model, User user) {
        Map<String, Object> map = userService.register(user);
        // 如果 service 返回的 map 是空，说明没有错误消息，注册成功
        if (map == null || map.isEmpty()) {
            model.addAttribute("msg", "注册成功, 我们已经向您的邮箱发送了一封激活邮件，请尽快激活!");
            // 下面的这个如果点击了将会找到 controller 中的 index 路径
            model.addAttribute("target", "/index");
            return "/site/operate-result";
        } else {
            // 否则就将错误消息返回给前端
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            model.addAttribute("emailMsg", map.get("emailMsg"));
            return "/site/register";
        }
    }

    /**
     * 激活用户：用户点击邮箱发送的激活链接，就访问到此路径
     * @param model
     * @param userId
     * @param code  激活码
     * @return
     * http://localhost:8080/community/activation/用户id/激活码
     */
    @GetMapping("/activation/{userId}/{code}")
    public String activation(Model model, @PathVariable("userId") int userId,
                             @PathVariable("code") String code) {
        // 在 service 层验证激活结果
        int result = userService.activation(userId, code);

        if (result == ACTIVATION_SUCCESS) {
            model.addAttribute("msg", "激活成功，您的账号可以正常使用！");
            model.addAttribute("target", "/login");
        }
        else if (result == ACTIVATION_REPEAT) {
            model.addAttribute("msg", "无效操作，您的账号已被激活过！");
            model.addAttribute("target", "/index");
        }
        else {
            model.addAttribute("msg", "激活失败，您提供的激活码不正确！");
            model.addAttribute("target", "/index");
        }
        return "/site/operate-result";
    }

    /**
     * 生成验证码，并存入 Redis
     */
    @GetMapping("/captcha")
    public void getCaptcha(HttpServletResponse response) {
        // 生成验证码
        // 生成随机字符
        String text = captchaProducer.createText();
        logger.info("验证码" + text);
        // 生验证码成图片，并将生成的随机字符置入该图片中
        BufferedImage image = captchaProducer.createImage(text);

        // 验证码的归属者
        // 获得一个随机字符串，由于用户还没登录，没法通过用户 id 来唯一的对应它的验证码
        // 生成一个随机 id 来暂时代替这个用户，将其和对应的验证码暂时存入 Redis 中
        String captchaOwner = CommunityUtil.generateUUID();
        // 将验证码放入 Cookie
        Cookie cookie = new Cookie("captchaOwner", captchaOwner);
        // 设置 cookie 存活时间
        cookie.setMaxAge(60);
        // 设置访问范围
        cookie.setPath(contextPath);
        // 将 Cookie 增加到响应中
        response.addCookie(cookie);
        // 将验证码存入 redis
        String redisKey = RedisKeyUtil.getCaptchaKey(captchaOwner);
        // key = 用户的随机 id，value = 生成的验证码，存活时间 60s
        redisTemplate.opsForValue().set(redisKey, text, 60, TimeUnit.SECONDS);

        // 将图片输出给浏览器
        response.setContentType("image/png");
        try {
            ServletOutputStream os = response.getOutputStream();
            ImageIO.write(image, "png", os);
        } catch (IOException e) {
            logger.error("响应验证码失败", e.getMessage());
        }
    }


    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @param code 用户填写的验证码
     * @param rememberMe 是否记住我（点击记住我后，凭证的有效期延长）
     * @param model
     * @param response
     * @param captchaOwner 从 cookie 中取出的 captchaOwner（用户的随机id）
     * @return
     */
    @PostMapping("/login")
    public String login(@RequestParam("username") String username,
                        @RequestParam("password") String password,
                        @RequestParam("code") String code,
                        @RequestParam(value = "rememberMe", required = false) boolean rememberMe,
                        Model model, HttpServletResponse response,
                        @CookieValue("captchaOwner") String captchaOwner) {

        // 检查验证码，根据生成的随机字符串（代表要登录的用户）去查找对应的验证码（在 redis 中）
        String captcha = null;
        // 如果随机字符串非空，从 redis 中得到该字符串对应的验证码
        if (StringUtils.isNotBlank(captchaOwner)) {
            String redisKey = RedisKeyUtil.getCaptchaKey(captchaOwner);
            captcha = (String) redisTemplate.opsForValue().get(redisKey);
        }

        // 判断验证码是否正确，如果不正确，提示用户
        if (StringUtils.isBlank(captcha) || StringUtils.isBlank(code) || !captcha.equalsIgnoreCase(code)) {
            model.addAttribute("codeMsg", "验证码错误");
            return "/site/login";
        }

        // 凭证过期时间（是否记住我）：默认是12小时，记住我是100天
        int expiredSeconds = rememberMe ? REMEMBER_EXPIRED_SECONDS : DEFAULT_EXPIRED_SECONDS;

        // 验证用户名和密码
        Map<String, Object> map = userService.login(username, password, expiredSeconds);
        // 如果 service 层返回的 map 中键是 trick，说明用户登录成功
        if (map.containsKey("ticket")) {
            // 账号和密码均正确，则服务端会生成 ticket，浏览器通过 cookie 存储 ticket
            Cookie cookie = new Cookie("ticket", map.get("ticket").toString());
            // cookie 有效范围
            cookie.setPath(contextPath);
            // 设置最大存活时间
            cookie.setMaxAge(expiredSeconds);
            response.addCookie(cookie);
            // 带有这种 重定向的后记进入的是controller路径，而不是页面的名称
            return "redirect:/index";
        }
        else {
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            return "/site/login";
        }
    }

    /**
     * 用户登出
     * @param ticket 设置凭证状态为无效
     * @return
     */
    @GetMapping("/logout")
    public String logout(@CookieValue("ticket") String ticket) {
        userService.logout(ticket);
        SecurityContextHolder.clearContext();
        // 用户登出之后重定向到登录页面
        return "redirect:/login";
    }

    /**
     * 验证用户输入的图片验证码是否和 redis 中存入的是否相等
     * @param captchaOwner 从 cookie 中取出的 captchaOwner
     * @param checkCode 用户输入的图片验证码
     * @return  失败则返回原因，验证成功返回 “”
     */
    private String checkCaptchaCode(String captchaOwner, String checkCode) {
        if (StringUtils.isBlank(checkCode)) {
            return "未发现输入的图片验证码";
        }
        String redisKey = RedisKeyUtil.getCaptchaKey(captchaOwner);
        String captchaValue = (String) redisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isBlank(captchaValue)) {
            return "图片验证码过期";
        }
        // 比较用户输入的验证码和 redis 中取出的验证码是否一致
        else if (!captchaValue.equalsIgnoreCase(checkCode)) {
            return "图片验证码错误";
        }
        return "";
    }

    /**
     * 重置密码（用于忘记密码）
     * @param username  账号
     * @param password  新密码
     * @param emailVerifyCode 邮箱验证码
     * @param captcha 用户输入验证码
     * @param captchaOwner 验证码所属者
     * @return
     */
    @PostMapping("/resetPwd")
    @ResponseBody
    public Map<String, Object> resetPwd(@RequestParam("username") String username,
                                        @RequestParam("password") String password,
                                        @RequestParam("emailVerifyCode") String emailVerifyCode,
                                        @RequestParam("captchaCode") String captcha,
                                        @CookieValue("captchaOwner") String captchaOwner) {
        Map<String, Object> map = new HashMap<>(4);
        // 检查图片验证码
        String captchaCheckRst = checkCaptchaCode(captchaOwner, captcha);
        // 如果返回值非空，说明验证有误
        if (StringUtils.isNotBlank(captchaCheckRst)) {
            map.put("status", "1");
            map.put("errMsg", captchaCheckRst);
        }

        // 检查邮件验证码
        String emailVerifyCodeCheckRst = checkRedisResetPwdEmailCode(username, emailVerifyCode);
        // 如果返回值费非空，说明验证操作有误
        if (StringUtils.isNotBlank(emailVerifyCodeCheckRst)) {
            map.put("status", "1");
            map.put("errMsg", emailVerifyCodeCheckRst);
        }

        // 执行重置密码操作
        Map<String, Object> stringObjectMap = userService.doResetPwd(username, password);
        String usernameMsg = (String) stringObjectMap.get("errMsg");
        if (StringUtils.isBlank(usernameMsg)) {
            map.put("status", "0");
            map.put("msg", "重置密码成功");
            map.put("target", contextPath + "/login");
        }
        return map;
    }

    /**
     * 检查邮件验证码
     * @param username 用户名
     * @param checkCode 用户输入的邮件验证码
     * @return 验证成功返回“”，失败则返回原因
     */
    private String checkRedisResetPwdEmailCode(String username, String checkCode) {
        // 判空
        if (StringUtils.isBlank(checkCode)) {
            return "请输入邮件验证码";
        }
        // 这里的 redis 的 key 是由 service 发送邮件验证码的时候设置的格式，保持一致就好了
        final String redisKey = "EmailCode4ResetPwd:" + username;
        // 获取存入 redis 中的邮件验证码
        String emailVerifyCodeInRedis = (String) redisTemplate.opsForValue().get(redisKey);
        // 判空
        if (StringUtils.isBlank(emailVerifyCodeInRedis)) {
            return "邮件验证码已过期";
        }
        // 检查用户输入的邮件验证码和 redis 中的是否一致
        else if (!emailVerifyCodeInRedis.equalsIgnoreCase(checkCode)) {
            return "邮件验证码错误";
        }
        // 用户输入的邮件验证码没有问题
        return "";
    }

    /**
     * 发送邮件验证码（用于重置密码）
     * @param captchaOwner 从 cookie 中取出的 captchaOwner
     * @param captcha 用户输入的图片验证码
     * @param username 用户输入的需要找回的账号
     * @return
     */
    @PostMapping("/sendEmailCodeForResetPwd")
    @ResponseBody
    public Map<String, Object> sendEmailCodeForResetPwd(@CookieValue("captchaOwner") String captchaOwner,
                                                        @RequestParam("captcha") String captcha,
                                                        @RequestParam("username") String username) {
        HashMap<String, Object> map = new HashMap<>(3);
        //  检查图片验证码，图片验证码正确时候才能发送邮箱验证码
        String captchaCheckRst = checkCaptchaCode(captchaOwner, captcha);
        if (StringUtils.isNotBlank(captchaCheckRst)) {
            map.put("status", "1");
            map.put("errMsg", captchaCheckRst);
        }
        Map<String, Object> stringObjectMap = userService.doSendEmailCode4ResetPwd(username);
        String usernameMsg = (String) stringObjectMap.get("errMsg");
        if (StringUtils.isBlank(usernameMsg)) {
            map.put("status", "0");
            map.put("msg", "已经往您的邮箱发送了一封验证码邮件，请查收！");
        }
        return map;
    }

}















