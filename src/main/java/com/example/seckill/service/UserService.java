package com.example.seckill.service;

import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.common.MD5Util;
import com.example.seckill.domain.User;
import com.example.seckill.vo.LoginVo;
import com.example.seckill.dao.UserMapper;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getById(Long id) {
        return userMapper.getById(id);
    }


    public User login(LoginVo loginVo) {
        User user = userMapper.getById(Long.valueOf(loginVo.getMobile()));
        if (user == null) {
          throw new MiaoshaException(CodeMsg.MOBILE_NOT_EXIST);
        }
        String dbPass = MD5Util.inputPassToDbPass(loginVo.getPassword(), user.getSalt());
        if (!dbPass.equals(user.getPassword())) {
          throw new MiaoshaException(CodeMsg.PASSWORD_ERROR);
        }
        return user;
    }
}




