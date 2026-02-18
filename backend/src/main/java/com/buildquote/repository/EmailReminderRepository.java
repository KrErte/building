package com.buildquote.repository;

import com.buildquote.entity.EmailReminder;
import com.buildquote.entity.RfqEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmailReminderRepository extends JpaRepository<EmailReminder, UUID> {

    int countByRfqEmail(RfqEmail rfqEmail);
}
