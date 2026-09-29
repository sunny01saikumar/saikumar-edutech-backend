package com.nexus.ai.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${nexus.mail.from-email:saikumarnexus46@gmail.com}")
    private String fromEmail;

    @Value("${nexus.mail.sender-name:NEXUS AI Security Team}")
    private String senderName;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendLoginNotificationEmail(String recipientEmail, String recipientName, String role) {
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            return;
        }

        if (mailPassword == null || mailPassword.trim().isEmpty()) {
            log.info("[Email Service] Login email notification for [{}] skipped (SPRING_MAIL_PASSWORD not configured).", recipientEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, senderName));
            helper.setTo(recipientEmail.trim());
            helper.setSubject("Security Alert: New Sign-in to your NEXUS AI Account");

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"));
            String htmlContent = buildLoginEmailTemplate(recipientName, recipientEmail, role, timestamp);

            helper.setText(htmlContent, true);
            mailSender.send(message);

            log.info("[Email Service] Successfully sent login security alert email to [{}] from masked sender [{}]", recipientEmail, senderName);
        } catch (Exception e) {
            log.error("[Email Service] Failed to send login email to [{}]: {}", recipientEmail, e.getMessage());
        }
    }

    @Async
    public void sendRegistrationNotificationEmail(String recipientEmail, String recipientName) {
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            return;
        }

        if (mailPassword == null || mailPassword.trim().isEmpty()) {
            log.info("[Email Service] Registration email notification for [{}] skipped (SPRING_MAIL_PASSWORD not configured).", recipientEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, senderName));
            helper.setTo(recipientEmail.trim());
            helper.setSubject("Welcome to NEXUS AI - Account Successfully Created");

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"));
            String htmlContent = buildWelcomeEmailTemplate(recipientName, recipientEmail, timestamp);

            helper.setText(htmlContent, true);
            mailSender.send(message);

            log.info("[Email Service] Successfully sent welcome email to [{}]", recipientEmail);
        } catch (Exception e) {
            log.error("[Email Service] Failed to send welcome email to [{}]: {}", recipientEmail, e.getMessage());
        }
    }

    private String buildLoginEmailTemplate(String name, String email, String role, String timestamp) {
        String displayName = (name != null && !name.trim().isEmpty()) ? name.trim() : "NEXUS User";
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0B0F19; color: #E2E8F0; margin: 0; padding: 20px; }
                .container { max-width: 580px; margin: 0 auto; background: #0E1322; border: 1px solid #1E293B; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                .header { background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 50%, #06B6D4 100%); padding: 28px 24px; text-align: center; }
                .header h1 { color: #FFFFFF; font-size: 22px; font-weight: 800; margin: 0; letter-spacing: 0.5px; }
                .header p { color: #E0E7FF; font-size: 12px; margin: 4px 0 0 0; text-transform: uppercase; letter-spacing: 1px; }
                .content { padding: 30px 24px; }
                .greeting { font-size: 16px; font-weight: 600; color: #F8FAFC; margin-bottom: 12px; }
                .text { font-size: 14px; color: #94A3B8; line-height: 1.6; margin-bottom: 24px; }
                .details-box { background: #161E31; border: 1px solid #334155; border-radius: 12px; padding: 18px; margin-bottom: 24px; }
                .detail-row { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 10px; }
                .detail-row:last-child { margin-bottom: 0; }
                .detail-label { color: #64748B; font-weight: 500; }
                .detail-value { color: #38BDF8; font-weight: 600; font-family: monospace; }
                .security-note { background: rgba(244, 63, 94, 0.1); border-left: 4px solid #F43F5E; padding: 12px 16px; border-radius: 4px; font-size: 12px; color: #FDA4AF; line-height: 1.5; }
                .footer { padding: 20px 24px; background: #0A0D16; border-top: 1px solid #1E293B; text-align: center; font-size: 11px; color: #475569; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>NEXUS AI</h1>
                  <p>Security & Access Control Alert</p>
                </div>
                <div class="content">
                  <div class="greeting">Hello %s,</div>
                  <div class="text">
                    A new sign-in activity was detected for your account on the <strong>NEXUS AI Enterprise Platform</strong>.
                  </div>

                  <div class="details-box">
                    <div class="detail-row">
                      <span class="detail-label">Account Email:</span>
                      <span class="detail-value">%s</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Date & Time:</span>
                      <span class="detail-value">%s</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Access Role:</span>
                      <span class="detail-value">%s</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Security Status:</span>
                      <span class="detail-value" style="color: #34D399;">VERIFIED & ACTIVE</span>
                    </div>
                  </div>

                  <div class="security-note">
                    <strong>Security Notice:</strong> If this sign-in was performed by you, no further action is required. If you did not authorize this login, please update your account password immediately.
                  </div>
                </div>
                <div class="footer">
                  &copy; 2026 NEXUS AI Platform • Autonomous & Secure Enterprise AI Engine<br>
                  This is an automated system security notification.
                </div>
              </div>
            </body>
            </html>
            """.formatted(displayName, email, timestamp, role != null ? role : "ROLE_USER");
    }

    private String buildWelcomeEmailTemplate(String name, String email, String timestamp) {
        String displayName = (name != null && !name.trim().isEmpty()) ? name.trim() : "NEXUS User";
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0B0F19; color: #E2E8F0; margin: 0; padding: 20px; }
                .container { max-width: 580px; margin: 0 auto; background: #0E1322; border: 1px solid #1E293B; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                .header { background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 50%, #06B6D4 100%); padding: 28px 24px; text-align: center; }
                .header h1 { color: #FFFFFF; font-size: 22px; font-weight: 800; margin: 0; letter-spacing: 0.5px; }
                .content { padding: 30px 24px; }
                .greeting { font-size: 16px; font-weight: 600; color: #F8FAFC; margin-bottom: 12px; }
                .text { font-size: 14px; color: #94A3B8; line-height: 1.6; margin-bottom: 24px; }
                .footer { padding: 20px 24px; background: #0A0D16; border-top: 1px solid #1E293B; text-align: center; font-size: 11px; color: #475569; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>Welcome to NEXUS AI</h1>
                </div>
                <div class="content">
                  <div class="greeting">Welcome %s!</div>
                  <div class="text">
                    Your account (<strong>%s</strong>) was successfully registered on <strong>%s</strong>. You now have access to high-performance AI models, RAG document indexing, and coding tools.
                  </div>
                </div>
                <div class="footer">
                  &copy; 2026 NEXUS AI Platform • Enterprise AI Engine
                </div>
              </div>
            </body>
            </html>
            """.formatted(displayName, email, timestamp);
    }
}
