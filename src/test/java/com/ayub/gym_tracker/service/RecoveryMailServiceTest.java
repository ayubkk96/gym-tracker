package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.controller.PasswordResetController;
import com.ayub.gym_tracker.security.AuthRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryMailServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void sendsUsingConfiguredOriginAndDoesNotSendForUnknownAccounts() {
        var tokens = mock(PasswordResetService.class);
        var executor = mock(ThreadPoolTaskExecutor.class);
        var sender = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(executor).execute(any(Runnable.class));
        when(tokens.issue("known@example.test")).thenReturn(Optional.of(new PasswordResetService.Delivery("known@example.test", "test-token")));
        when(tokens.issue("missing@example.test")).thenReturn(Optional.empty());
        var mail = new RecoveryMailService(tokens, executor, provider, true,
                "https://tracker.example.test", "sender@example.test");
        mail.request("known@example.test");
        mail.request("missing@example.test");
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(1)).send(message.capture());
        assertTrue(message.getValue().getText().contains("https://tracker.example.test/reset-password.html#token=test-token"));
        assertEquals("sender@example.test", message.getValue().getFrom());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusesInsecureOrUnconfiguredEnabledRecovery() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        assertThrows(IllegalStateException.class, () -> new RecoveryMailService(
                mock(PasswordResetService.class), mock(ThreadPoolTaskExecutor.class), provider,
                true, "http://tracker.example.test", "sender@example.test"));
        when(provider.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> new RecoveryMailService(
                mock(PasswordResetService.class), mock(ThreadPoolTaskExecutor.class), provider,
                true, "https://tracker.example.test", "sender@example.test"));
    }

    @Test
    void knownUnknownAndAddressThrottledRequestsHaveIdenticalResponses() {
        var mail = mock(RecoveryMailService.class);
        var limiter = mock(AuthRateLimiter.class);
        when(mail.enabled()).thenReturn(true);
        when(limiter.allow(anyString(), eq(3), eq(900))).thenReturn(true, true, false);
        var controller = new PasswordResetController(mock(PasswordResetService.class), mail, limiter);
        var first = controller.request(new PasswordResetController.ResetRequest("known@example.test"));
        var second = controller.request(new PasswordResetController.ResetRequest("missing@example.test"));
        var third = controller.request(new PasswordResetController.ResetRequest("known@example.test"));
        assertEquals(202, first.getStatusCode().value());
        assertEquals(first, second);
        assertEquals(first, third);
        verify(mail, times(2)).request(anyString());
    }
}
