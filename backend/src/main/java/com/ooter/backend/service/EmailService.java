package com.ooter.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ✅ Existing OTP Email method
    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("OOTER Email Verification OTP");
        message.setText("Your OTP is: " + otp + "\nThis will expire in 5 minutes.");

        mailSender.send(message);
    }

    // ✅ New Password Reset Email method
    public void sendPasswordResetEmail(String toEmail, String resetToken, String userName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Reset Your Password - Ooter");
            
            String emailBody = String.format(
                "Hello %s,\n\n" +
                "You have requested to reset your password for your Ooter account.\n\n" +
                "Click the link below to reset your password:\n" +
                "ooter://reset-password?token=%s\n\n" +
                "If the link doesn't work, copy and paste this URL into your browser:\n" +
                "https://yourapp.com/reset-password?token=%s\n\n" +
                "This link will expire in 1 hour.\n\n" +
                "If you didn't request this password reset, please ignore this email.\n\n" +
                "Best regards,\n" +
                "Team Ooter",
                userName != null ? userName : "User",
                resetToken,
                resetToken
            );
            
            message.setText(emailBody);
            
            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}
