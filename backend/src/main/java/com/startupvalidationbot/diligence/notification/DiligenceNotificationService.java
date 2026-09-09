package com.startupvalidationbot.diligence.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.diligence.DiligenceDomain.Packet;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.Status;
import com.startupvalidationbot.radar.ContentHash;

@Service
public class DiligenceNotificationService {
    private final DiligenceStore store;
    private final DiligenceEmailSender sender;
    private final String recipient;
    private final String appUrl;

    public DiligenceNotificationService(DiligenceStore store, DiligenceEmailSender sender,
            @Value("${startup.intelligence.email-recipient:${deal.scout.email-recipient:}}") String recipient,
            @Value("${radar.app-url:http://127.0.0.1:5173/#/radar}") String appUrl) {
        this.store = store; this.sender = sender; this.recipient = recipient == null ? "" : recipient.trim();
        this.appUrl = appUrl;
    }

    public boolean queueReady(Packet packet) {
        if (recipient.isBlank()
                || packet.status() != com.startupvalidationbot.diligence.DiligenceDomain.PacketStatus.READY) return false;
        String fingerprint = ContentHash.sha256("DILIGENCE_READY|" + packet.offeringId());
        String subject = "Startup Intelligence - Diligence ready: " + packet.companyName();
        String disclaimer = "Research only. Not an investment recommendation.";
        String text = packet.companyName() + " has a public-evidence diligence packet ready for review.\n\n"
                + "Platform: " + packet.platform() + "\nSecurity: " + value(packet.securityType())
                + "\nMinimum: " + value(packet.minimumInvestment()) + "\nDiligence: READY\n\n"
                + "Review: " + appUrl.replace("#/radar", "#/review/" + packet.id()) + "\n\n" + disclaimer;
        String html = "<h1>Diligence ready: " + escape(packet.companyName()) + "</h1><p>Platform: "
                + escape(packet.platform()) + "</p><p>Security: " + escape(value(packet.securityType()))
                + "</p><p><a href=\"" + escape(appUrl.replace("#/radar", "#/review/" + packet.id()))
                + "\">Review diligence</a></p><p>" + disclaimer + "</p>";
        return store.queueNotification("DILIGENCE_READY", "DILIGENCE_PACKET", packet.id(), fingerprint,
                recipient, subject, text, html);
    }

    public boolean queueNewConfirmed(Offering offering, Packet packet) {
        if (recipient.isBlank() || offering.matchStatus() != MatchStatus.CONFIRMED
                || offering.status() != Status.ACTIVE) return false;
        String fingerprint = ContentHash.sha256("NEW_CONFIRMED_OFFERING|ACTIVE|" + offering.id());
        String subject = "Startup Intelligence - New confirmed offering: " + packet.companyName();
        String text = packet.companyName() + " has a newly confirmed public Regulation Crowdfunding offering.\n\n"
                + "Platform: " + value(packet.platform()) + "\nSecurity: " + value(packet.securityType())
                + "\nReview: " + reviewUrl(packet.id()) + "\n\nResearch only. Not an investment recommendation.";
        String html = "<h1>New confirmed offering: " + escape(packet.companyName()) + "</h1><p>Platform: "
                + escape(value(packet.platform())) + "</p><p>Security: " + escape(value(packet.securityType()))
                + "</p><p><a href=\"" + escape(reviewUrl(packet.id())) + "\">Review public evidence</a></p>"
                + "<p>Research only. Not an investment recommendation.</p>";
        return store.queueNotification("NEW_CONFIRMED_OFFERING", "OFFERING", offering.id(), fingerprint,
                recipient, subject, text, html);
    }

    public boolean queueMaterialChange(PlatformCampaign previous, PlatformCampaign current, Packet packet) {
        if (recipient.isBlank() || previous == null || !materiallyChanged(previous, current)) return false;
        String fingerprint = ContentHash.sha256("MATERIAL_OFFERING_CHANGE|" + current.offeringId() + "|"
                + current.sourceFingerprint());
        String subject = "Startup Intelligence - Offering changed: " + packet.companyName();
        String text = packet.companyName() + " has a material public offering change.\n\n"
                + changeSummary(previous, current) + "\nReview: " + reviewUrl(packet.id())
                + "\n\nResearch only. Not an investment recommendation.";
        String html = "<h1>Offering changed: " + escape(packet.companyName()) + "</h1><p>"
                + escape(changeSummary(previous, current)) + "</p><p><a href=\"" + escape(reviewUrl(packet.id()))
                + "\">Review diligence</a></p><p>Research only. Not an investment recommendation.</p>";
        return store.queueNotification("MATERIAL_OFFERING_CHANGE", "OFFERING", current.offeringId(), fingerprint,
                recipient, subject, text, html);
    }

    public SendCounts sendPending() {
        int sent = 0; int failed = 0;
        if (!configured()) return new SendCounts(0, 0);
        for (DiligenceStore.NotificationEvent event : store.pendingNotifications(10)) {
            DiligenceEmailSender.SendResult result = sender.send(event.recipient(), event.subject(), event.text(), event.html());
            if (result.ok()) { store.notificationSent(event.id(), result.messageId()); sent++; }
            else { store.notificationFailed(event.id(), result.error()); failed++; }
        }
        return new SendCounts(sent, failed);
    }

    public boolean configured() { return sender.configured() && !recipient.isBlank(); }
    private String reviewUrl(long packetId) { return appUrl.replace("#/radar", "#/review/" + packetId); }
    private static boolean materiallyChanged(PlatformCampaign left, PlatformCampaign right) {
        return !java.util.Objects.equals(left.status(), right.status())
                || !java.util.Objects.equals(left.securityType(), right.securityType())
                || !sameMoney(left.valuation(), right.valuation())
                || !sameMoney(left.valuationCap(), right.valuationCap())
                || !sameMoney(left.targetAmount(), right.targetAmount())
                || !sameMoney(left.maximumAmount(), right.maximumAmount())
                || !java.util.Objects.equals(left.deadline(), right.deadline());
    }
    private static boolean sameMoney(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
    private static String changeSummary(PlatformCampaign left, PlatformCampaign right) {
        java.util.List<String> values = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(left.status(), right.status())) values.add("Status: " + left.status() + " -> " + right.status());
        if (!java.util.Objects.equals(left.securityType(), right.securityType())) values.add("Security terms changed");
        if (!sameMoney(left.valuation(), right.valuation()) || !sameMoney(left.valuationCap(), right.valuationCap())) values.add("Valuation or cap changed");
        if (!sameMoney(left.targetAmount(), right.targetAmount()) || !sameMoney(left.maximumAmount(), right.maximumAmount())) values.add("Target or maximum changed");
        if (!java.util.Objects.equals(left.deadline(), right.deadline())) values.add("Deadline changed");
        return String.join("; ", values) + ".";
    }
    private static String value(Object value) { return value == null ? "Could not establish" : value.toString(); }
    private static String escape(String value) { return value == null ? "" : value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    public record SendCounts(int sent, int failed) { }
}
