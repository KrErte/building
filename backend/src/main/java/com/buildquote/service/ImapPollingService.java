package com.buildquote.service;

import com.buildquote.entity.InboundEmail;
import com.buildquote.repository.InboundEmailRepository;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Properties;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.imap.enabled", havingValue = "true")
public class ImapPollingService {

    @Value("${mail.imap.host}")
    private String imapHost;

    @Value("${mail.imap.port:993}")
    private int imapPort;

    @Value("${mail.imap.username}")
    private String imapUsername;

    @Value("${mail.imap.password}")
    private String imapPassword;

    private final InboundEmailRepository inboundEmailRepository;
    private final EmailMatchingService emailMatchingService;

    @Scheduled(fixedDelayString = "${mail.imap.poll-interval-ms:60000}")
    public void pollEmails() {
        log.debug("Polling IMAP mailbox: {}", imapUsername);

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");

        Store store = null;
        Folder inbox = null;

        try {
            Session session = Session.getInstance(props);
            store = session.getStore("imaps");
            store.connect(imapHost, imapPort, imapUsername, imapPassword);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            log.debug("Found {} unread messages", messages.length);

            for (Message message : messages) {
                try {
                    processMessage((MimeMessage) message);
                    message.setFlag(Flags.Flag.SEEN, true);
                } catch (Exception e) {
                    log.error("Error processing message: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("IMAP polling error: {}", e.getMessage());
        } finally {
            try {
                if (inbox != null && inbox.isOpen()) inbox.close(false);
                if (store != null && store.isConnected()) store.close();
            } catch (MessagingException e) {
                log.warn("Error closing IMAP connection: {}", e.getMessage());
            }
        }
    }

    private void processMessage(MimeMessage message) throws MessagingException, IOException {
        String messageId = message.getMessageID();
        if (messageId != null && inboundEmailRepository.existsByMessageId(messageId)) {
            log.debug("Skipping already processed message: {}", messageId);
            return;
        }

        String from = message.getFrom() != null && message.getFrom().length > 0
                ? message.getFrom()[0].toString() : "unknown";
        String subject = message.getSubject();
        String body = extractTextContent(message);

        InboundEmail inboundEmail = InboundEmail.builder()
                .messageId(messageId)
                .fromEmail(from)
                .subject(subject)
                .body(body)
                .receivedAt(LocalDateTime.now())
                .processed(false)
                .build();

        inboundEmail = inboundEmailRepository.save(inboundEmail);
        log.info("Received inbound email from {} (subject: {})", from, subject);

        // Try to match and process
        emailMatchingService.matchAndProcess(inboundEmail);
    }

    private String extractTextContent(Message message) throws MessagingException, IOException {
        Object content = message.getContent();
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof Multipart) {
            return extractFromMultipart((Multipart) content);
        }
        return content.toString();
    }

    private String extractFromMultipart(Multipart multipart) throws MessagingException, IOException {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.isMimeType("text/plain")) {
                text.append(part.getContent().toString());
            } else if (part.isMimeType("text/html") && text.isEmpty()) {
                text.append(part.getContent().toString());
            } else if (part.getContent() instanceof Multipart) {
                text.append(extractFromMultipart((Multipart) part.getContent()));
            }
        }
        return text.toString();
    }
}
