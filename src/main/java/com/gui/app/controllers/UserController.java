package com.gui.app.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gui.app.entity.User;
import com.gui.app.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
public class UserController {

    final static Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @GetMapping("/user")
    public List<User> getUser(@RequestParam Map<String, String> params, @RequestBody String rawBodyString) {
        logger.info("getUser param: {}", params);
        logger.info("getUser body: {}", rawBodyString);

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("param", params);
        response.put("body", rawBodyString);

        // 从数据库查询用户数据
        List<User> users = userService.getUserList();
        response.put("users", users);
        response.put("total", users.size());

        logger.info("查询到用户数量: {}", users.size());
        return users;
    }

    @PostMapping("/user")
    public User createUser(@RequestBody User user) {
        // 创建用户
        userService.save(user);

        return userService.getUserById(user.getId());
    }

}
