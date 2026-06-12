package com.example.seckill.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.GlobalExceptionHandler;
import com.example.seckill.common.JwtUtil;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.domain.User;
import com.example.seckill.service.UserService;
import com.example.seckill.vo.LoginVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        // standaloneSetup 不会自动注册 @ControllerAdvice，需显式注册全局异常处理器
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_success_returnsToken() throws Exception {
        LoginVo loginVo = new LoginVo();
        loginVo.setMobile("13800000000");
        loginVo.setPassword("123456");

        User user = new User();
        user.setId(12345L);
        user.setNickname("TestUser");

        when(userService.login(any(LoginVo.class))).thenReturn(user);

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginVo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    void login_fail_returnsError() throws Exception {
        LoginVo loginVo = new LoginVo();
        loginVo.setMobile("13800000000");
        loginVo.setPassword("wrong_password");

        when(userService.login(any(LoginVo.class)))
                .thenThrow(new MiaoshaException(CodeMsg.PASSWORD_ERROR));

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginVo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500502))
                .andExpect(jsonPath("$.msg").value("密码错误"));
    }

    @Test
    void login_invalidMobile_returns400() throws Exception {
        LoginVo loginVo = new LoginVo();
        loginVo.setMobile("12345"); // 格式错误：不是 11 位手机号
        loginVo.setPassword("123456");

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginVo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("手机号格式错误"));
    }

    @Test
    void login_blankMobile_returns400() throws Exception {
        LoginVo loginVo = new LoginVo();
        loginVo.setMobile(""); // 手机号为空
        loginVo.setPassword("123456");

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginVo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("手机号不能为空"));
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        LoginVo loginVo = new LoginVo();
        loginVo.setMobile("13800000000");
        loginVo.setPassword(null); // 密码为 null，触发 @NotBlank

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginVo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("密码不能为空"));
    }

    @Test
    void login_shortPassword_returns400() throws Exception {
        LoginVo loginVo = new LoginVo();
        loginVo.setMobile("13800000000");
        loginVo.setPassword("123"); // 密码过短（少于 6 位）

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginVo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("密码长度必须在6-32位之间"));
    }

    @Test
    void profile_validToken_returnsUserWithoutSensitiveData() throws Exception {
        Long userId = 12345L;
        String token = JwtUtil.generateToken(userId);

        User user = new User();
        user.setId(userId);
        user.setNickname("TestUser");
        user.setPassword("secret_pwd");
        user.setSalt("random_salt");

        when(userService.getById(userId)).thenReturn(user);

        mockMvc.perform(get("/user/profile")
                .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("TestUser"))
                .andExpect(jsonPath("$.data.password").isEmpty())
                .andExpect(jsonPath("$.data.salt").isEmpty());
    }

    @Test
    void profile_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/user/profile")
                .header("token", "invalid-token-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("请先登录或提供合法的 Token"));
    }

    @Test
    void profile_missingToken_returns401() throws Exception {
        mockMvc.perform(get("/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("请先登录或提供合法的 Token"));
    }
}
