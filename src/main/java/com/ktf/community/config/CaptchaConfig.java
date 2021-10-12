package com.ktf.community.config;

import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Captcha 配置类（验证码）
 * 使用的是 Google 的工具类
 * @author github.com/kuangtf
 * @date 2021/9/5 10:15
 */
@Configuration
public class CaptchaConfig {

    @Bean
    public Producer captchaProducer() {

        Properties properties = new Properties();
        // 验证码的宽度
        properties.setProperty("kaptcha.image.width", "100");
        // 验证码的高度
        properties.setProperty("kaptcha.image.height", "40");
        // 字体大小
        properties.setProperty("kaptcha.textproducer.font.size", "32");
        // 字体颜色
        properties.setProperty("kaptcha.textproducer.font.color", "black");
        // 随机生成字符的范围
        properties.setProperty("kaptcha.textproducer.char.string", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        // 生成几个字符
        properties.setProperty("kaptcha.textproducer.char.length", "4");
        // 添加噪声
        properties.setProperty("kaptcha.textproducer.noise.impl", "com.google.code.kaptcha.impl.NoNoise");

        DefaultKaptcha kaptcha = new DefaultKaptcha();
        Config config = new Config(properties);
        kaptcha.setConfig(config);
        return kaptcha;
    }

}
