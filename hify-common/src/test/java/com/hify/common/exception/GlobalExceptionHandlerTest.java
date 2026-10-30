package com.hify.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bizExceptionReturnsItsErrorCode() throws Exception {
        mockMvc.perform(get("/test/biz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void bizExceptionCustomMessageOverridesDesc() throws Exception {
        mockMvc.perform(get("/test/biz-custom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("id 不能为空"));
    }

    @Test
    void validationFailureReturnsParamErrorWithFieldMessage() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("name: 名称不能为空"));
    }

    @Test
    void unknownExceptionReturnsInternalError() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("系统内部错误"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/biz")
        public void biz() {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        @GetMapping("/test/biz-custom")
        public void bizCustom() {
            throw new BizException(ErrorCode.PARAM_ERROR, "id 不能为空");
        }

        @PostMapping("/test/valid")
        public void valid(@Valid @RequestBody TestReq req) {
        }

        @GetMapping("/test/boom")
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }

    @Data
    static class TestReq {

        @NotBlank(message = "名称不能为空")
        private String name;
    }
}
