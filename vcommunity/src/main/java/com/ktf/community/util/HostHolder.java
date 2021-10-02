package com.ktf.community.util;

import com.ktf.community.entity.User;
import org.springframework.stereotype.Component;


/**
 *
 *
 * @author github.com/kuangtf
 * @date 2021/9/4 20:18
 */
@Component
public class HostHolder {

    private ThreadLocal<User> users = new ThreadLocal<>();

    // 存储 User
    public void setUsers(User user) {
        users.set(user);
    }

    // 获取 User
    public User getUser() {
        return users.get();
    }

    //  清理
    public void clear() {
        users.remove();
    }
}
