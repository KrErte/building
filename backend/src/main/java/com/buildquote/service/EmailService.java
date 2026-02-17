package com.buildquote.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${resend.dev.mode:true}")
    private boolean devMode;

    @Value("${resend.dev.recipient:kristo.erte@gmail.com}")
    private String devRecipient;

    @Value("${app.base.url:http://localhost:4200}")
    private String appBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean sendRfqEmail(String toEmail, String toName, String campaignTitle, String category,
                                 String location, String quantity, String unit, String specifications,
                                 String bidToken) {
        // In dev mode, send all emails to the dev recipient
        String actualRecipient = devMode ? devRecipient : toEmail;

        String bidUrl = appBaseUrl + "/bid/" + bidToken;

        String subject = (devMode ? "[DEV] " : "") + "Hinnaparing: " + campaignTitle + " (" + location + ")";

        String htmlBody = buildRfqEmailHtml(toName, campaignTitle, category, location, quantity, unit,
                specifications, bidUrl, devMode ? toEmail : null);

        return sendEmail(actualRecipient, subject, htmlBody);
    }

    public boolean sendEmail(String to, String subject, String htmlBody) {
        if (resendApiKey == null || resendApiKey.isEmpty()) {
            log.warn("Resend API key not configured - email not sent to: {}", to);
            log.info("Would send email:\nTo: {}\nSubject: {}\n", to, subject);
            return false;
        }

        // In dev mode, redirect to dev recipient and prefix subject
        String actualRecipient = devMode ? devRecipient : to;
        String actualSubject = (devMode && !subject.startsWith("[DEV]")) ? "[DEV] " + subject : subject;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", "BuildQuote <" + fromEmail + ">");
            payload.put("to", List.of(actualRecipient));
            payload.put("subject", actualSubject);
            payload.put("html", htmlBody);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.resend.com/emails",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent successfully to: {}", to);
                return true;
            } else {
                log.error("Failed to send email to {}. Status: {}", to, response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private String buildRfqEmailHtml(String supplierName, String title, String category,
                                      String location, String quantity, String unit,
                                      String specifications, String bidUrl, String originalEmail) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset='UTF-8'></head>");
        html.append("<body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>");

        // Header
        html.append("<div style='background: #8b5cf6; color: white; padding: 20px; border-radius: 8px 8px 0 0;'>");
        html.append("<h1 style='margin: 0; font-size: 24px;'>BuildQuote</h1>");
        html.append("<p style='margin: 8px 0 0 0; opacity: 0.9;'>Uus hinnaparing</p>");
        html.append("</div>");

        // Content
        html.append("<div style='background: #f9fafb; padding: 24px; border: 1px solid #e5e7eb;'>");

        html.append("<p style='font-size: 16px;'>Tere, <strong>").append(supplierName).append("</strong>!</p>");
        html.append("<p>Teil on uus hinnaparing:</p>");

        // Job details
        html.append("<div style='background: white; padding: 16px; border-radius: 8px; margin: 16px 0;'>");
        html.append("<h2 style='margin: 0 0 12px 0; color: #1f2937;'>").append(title).append("</h2>");
        html.append("<table style='width: 100%; border-collapse: collapse;'>");
        html.append("<tr><td style='padding: 8px 0; color: #6b7280;'>Kategooria:</td><td style='padding: 8px 0;'><strong>").append(category).append("</strong></td></tr>");
        html.append("<tr><td style='padding: 8px 0; color: #6b7280;'>Asukoht:</td><td style='padding: 8px 0;'><strong>").append(location).append("</strong></td></tr>");
        html.append("<tr><td style='padding: 8px 0; color: #6b7280;'>Maht:</td><td style='padding: 8px 0;'><strong>").append(quantity).append(" ").append(unit).append("</strong></td></tr>");
        html.append("</table>");

        if (specifications != null && !specifications.isEmpty()) {
            html.append("<div style='margin-top: 12px; padding-top: 12px; border-top: 1px solid #e5e7eb;'>");
            html.append("<p style='color: #6b7280; margin: 0 0 8px 0;'>Kirjeldus:</p>");
            html.append("<p style='margin: 0;'>").append(specifications).append("</p>");
            html.append("</div>");
        }
        html.append("</div>");

        // CTA Button
        html.append("<div style='text-align: center; margin: 24px 0;'>");
        html.append("<a href='").append(bidUrl).append("' style='display: inline-block; background: #8b5cf6; color: white; padding: 14px 32px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 16px;'>Esita pakkumine</a>");
        html.append("</div>");

        // Dev mode notice
        if (originalEmail != null) {
            html.append("<div style='background: #fef3c7; padding: 12px; border-radius: 8px; margin-top: 16px;'>");
            html.append("<p style='margin: 0; color: #92400e; font-size: 14px;'><strong>DEV MODE:</strong> Originaalne saaja oli: ").append(originalEmail).append("</p>");
            html.append("</div>");
        }

        html.append("</div>");

        // Footer
        html.append("<div style='padding: 16px; text-align: center; color: #6b7280; font-size: 12px;'>");
        html.append("<p>See kiri saadeti BuildQuote platvormi kaudu.</p>");
        html.append("<p>Kui te ei soovi enam paringuid saada, voite sellest kirjast mootminna.</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    public boolean sendOnboardingEmail(String toEmail, String companyName, String onboardUrl) {
        String subject = (devMode ? "[DEV] " : "") + "Registreerige oma ettevõte BuildQuote platvormil";
        String htmlBody = buildOnboardingEmailHtml(companyName, onboardUrl, devMode ? toEmail : null);
        return sendEmail(toEmail, subject, htmlBody);
    }

    private String buildOnboardingEmailHtml(String companyName, String onboardUrl, String originalEmail) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset='UTF-8'></head>");
        html.append("<body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f3f4f6;'>");

        // Header
        html.append("<div style='background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%); color: white; padding: 32px; border-radius: 12px 12px 0 0; text-align: center;'>");
        html.append("<h1 style='margin: 0; font-size: 28px; font-weight: 700;'>BuildQuote</h1>");
        html.append("<p style='margin: 12px 0 0 0; opacity: 0.9; font-size: 16px;'>AI-põhine ehitushangete platvorm</p>");
        html.append("</div>");

        // Content
        html.append("<div style='background: white; padding: 32px; border: 1px solid #e5e7eb; border-top: none;'>");

        html.append("<p style='font-size: 18px; color: #1f2937;'>Tere, <strong>").append(companyName).append("</strong>!</p>");

        html.append("<p style='font-size: 16px; color: #4b5563; line-height: 1.6;'>");
        html.append("BuildQuote on uus AI-põhine ehitushangete platvorm, mis ühendab ehitajaid ja tegijaid. ");
        html.append("Meie platvormil saate tasuta hinnapäringuid otse oma valdkonnas.");
        html.append("</p>");

        // Benefits
        html.append("<div style='background: #f9fafb; padding: 20px; border-radius: 8px; margin: 24px 0;'>");
        html.append("<h3 style='margin: 0 0 16px 0; color: #1f2937;'>Miks liituda?</h3>");
        html.append("<ul style='margin: 0; padding-left: 20px; color: #4b5563;'>");
        html.append("<li style='margin-bottom: 8px;'>✅ Tasuta registreerimine ja kasutamine</li>");
        html.append("<li style='margin-bottom: 8px;'>✅ Hinnapäringud otse teie postkasti</li>");
        html.append("<li style='margin-bottom: 8px;'>✅ Valige oma teeninduspiirkonnad ja kategooriad</li>");
        html.append("<li style='margin-bottom: 8px;'>✅ Rohkem kliente, vähem otsingut</li>");
        html.append("</ul>");
        html.append("</div>");

        // CTA Button
        html.append("<div style='text-align: center; margin: 32px 0;'>");
        html.append("<a href='").append(onboardUrl).append("' style='display: inline-block; background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%); color: white; padding: 16px 40px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 18px; box-shadow: 0 4px 14px rgba(139, 92, 246, 0.4);'>Registreeri oma ettevõte</a>");
        html.append("</div>");

        html.append("<p style='font-size: 14px; color: #6b7280; text-align: center;'>");
        html.append("Registreerimine võtab vaid 1 minuti.");
        html.append("</p>");

        // Dev mode notice
        if (originalEmail != null) {
            html.append("<div style='background: #fef3c7; padding: 12px; border-radius: 8px; margin-top: 24px;'>");
            html.append("<p style='margin: 0; color: #92400e; font-size: 14px;'><strong>DEV MODE:</strong> Originaalne saaja: ").append(originalEmail).append("</p>");
            html.append("</div>");
        }

        html.append("</div>");

        // Footer
        html.append("<div style='padding: 24px; text-align: center; color: #6b7280; font-size: 12px; background: #f9fafb; border-radius: 0 0 12px 12px;'>");
        html.append("<p style='margin: 0 0 8px 0;'>See kiri saadeti BuildQuote platvormi kaudu.</p>");
        html.append("<p style='margin: 0;'>© 2024 BuildQuote OÜ. Kõik õigused kaitstud.</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }
}
