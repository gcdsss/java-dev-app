package com.gui.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gui.app.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
