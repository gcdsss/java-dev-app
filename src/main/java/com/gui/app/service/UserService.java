package com.gui.app.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gui.app.entity.User;
import com.gui.app.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    /**
     * 获取所有用户列表
     */
    public List<User> getUserList() {
        return list();
    }

    /**
     * 根据ID获取用户
     */
    public User getUserById(Long id) {
        return getById(id);
    }
}
