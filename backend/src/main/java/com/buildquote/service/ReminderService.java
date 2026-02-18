package com.buildquote.service;

import com.buildquote.entity.EmailReminder;
import com.buildquote.entity.RfqCampaign;
import com.buildquote.entity.RfqEmail;
import com.buildquote.repository.EmailReminderRepository;
import com.buildquote.repository.RfqCampaignRepository;
import com.buildquote.repository.RfqEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderService {

    private final RfqEmailRepository rfqEmailRepository;
    private final RfqCampaignRepository campaignRepository;
    private final EmailReminderRepository reminderRepository;
    private final EmailService emailService;

    @Value("${reminder.delay-hours:72}")
    private int reminderDelayHours;

    @Value("${reminder.max-count:2}")
    private int maxReminders;

    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void checkAndSendReminders() {
        List<RfqCampaign> activeCampaigns = campaignRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");

        for (RfqCampaign campaign : activeCampaigns) {
            List<RfqEmail> rfqEmails = rfqEmailRepository.findByCampaign(campaign);

            for (RfqEmail rfqEmail : rfqEmails) {
                // Only remind for emails that were sent but not responded
                if (!"SENT".equals(rfqEmail.getStatus()) && !"OPENED".equals(rfqEmail.getStatus())) {
                    continue;
                }

                // Check if enough time has passed since sent/last reminder
                LocalDateTime lastContact = rfqEmail.getRemindedAt() != null
                        ? rfqEmail.getRemindedAt()
                        : rfqEmail.getSentAt();

                if (lastContact == null || lastContact.plusHours(reminderDelayHours).isAfter(LocalDateTime.now())) {
                    continue;
                }

                // Check reminder count
                int remindersSent = reminderRepository.countByRfqEmail(rfqEmail);
                if (remindersSent >= maxReminders) {
                    continue;
                }

                // Send reminder
                sendReminder(rfqEmail, campaign, remindersSent + 1);
            }
        }
    }

    private void sendReminder(RfqEmail rfqEmail, RfqCampaign campaign, int reminderNumber) {
        try {
            String subject = "Meeldetuletus: " + campaign.getTitle() + " - Ootame teie pakkumist";
            String body = buildReminderHtml(rfqEmail, campaign, reminderNumber);

            boolean sent = emailService.sendEmail(rfqEmail.getSupplierEmail(), subject, body);

            if (sent) {
                EmailReminder reminder = EmailReminder.builder()
                        .rfqEmail(rfqEmail)
                        .campaign(campaign)
                        .reminderNumber(reminderNumber)
                        .sentAt(LocalDateTime.now())
                        .build();
                reminderRepository.save(reminder);

                rfqEmail.setRemindedAt(LocalDateTime.now());
                rfqEmail.setReminderCount(reminderNumber);
                rfqEmailRepository.save(rfqEmail);

                log.info("Reminder #{} sent to {} for campaign {}",
                        reminderNumber, rfqEmail.getSupplierEmail(), campaign.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send reminder to {}: {}", rfqEmail.getSupplierEmail(), e.getMessage());
        }
    }

    private String buildReminderHtml(RfqEmail rfqEmail, RfqCampaign campaign, int reminderNumber) {
        return String.format("""
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
            <div style="background: #f59e0b; color: white; padding: 20px; border-radius: 8px 8px 0 0;">
                <h1 style="margin: 0;">Meeldetuletus: Pakkumise esitamine</h1>
            </div>
            <div style="background: #f9fafb; padding: 24px; border: 1px solid #e5e7eb;">
                <p>Lugupeetud %s,</p>
                <p>Saatsime teile hinnapäringu "%s" ja ootame endiselt teie pakkumist.</p>
                <p>Palun esitage oma pakkumine alloleva lingi kaudu:</p>
                <p style="text-align: center; margin: 24px 0;">
                    <a href="${app.base.url}/bid/%s"
                       style="background: #10b981; color: white; padding: 12px 24px;
                              text-decoration: none; border-radius: 6px; font-weight: bold;">
                        Esita pakkumine
                    </a>
                </p>
            </div>
            </body></html>
            """, rfqEmail.getSupplierName(), campaign.getTitle(), rfqEmail.getToken());
    }
}
