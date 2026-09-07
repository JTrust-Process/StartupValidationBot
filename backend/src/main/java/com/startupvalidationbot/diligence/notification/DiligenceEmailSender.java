package com.startupvalidationbot.diligence.notification;

public interface DiligenceEmailSender {
    boolean configured();
    SendResult send(String to, String subject, String text, String html);
    record SendResult(boolean ok, String messageId, String error) { }
}
