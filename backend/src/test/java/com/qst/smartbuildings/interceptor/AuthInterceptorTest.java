package com.qst.smartbuildings.interceptor;

import com.qst.smartbuildings.util.RedisClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {
    @Mock
    private RedisClient redisClient;
    @InjectMocks
    private AuthInterceptor interceptor;

    @Test
    void developmentModeDoesNotRequireRedisAuthentication() throws Exception {
        ReflectionTestUtils.setField(interceptor, "authEnabled", false);

        boolean allowed = interceptor.preHandle(
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/asset/sensor/data/push"),
                new org.springframework.mock.web.MockHttpServletResponse(),
                new Object());

        assertThat(allowed).isTrue();
        verifyNoInteractions(redisClient);
    }
}
