package com.startupvalidationbot.radar.worker;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.startupvalidationbot.radar.service.RadarJobService;

@Configuration
@EnableScheduling
@Profile("worker")
public class RadarWorkerConfiguration {
    private final RadarJobService jobs;

    public RadarWorkerConfiguration(RadarJobService jobs) {
        this.jobs = jobs;
    }

    @Scheduled(cron = "${radar.discovery-cron:0 0 6 * * *}", zone = "${radar.time-zone:America/New_York}")
    public void discover() {
        jobs.run("discovery", null, true);
    }

    @Scheduled(cron = "${offering.discovery-cron:0 15 7 * * *}", zone = "${radar.time-zone:America/New_York}")
    public void discoverOfferings() {
        jobs.run("offering-discovery", null, true);
    }

    @Scheduled(cron = "${autonomous.diligence-cron:0 45 7 * * *}", zone = "${radar.time-zone:America/New_York}")
    public void autonomousDiligence() {
        jobs.run("autonomous-diligence", null, true);
    }

    @Scheduled(cron = "${radar.watchlist-cron:0 30 6 * * *}", zone = "${radar.time-zone:America/New_York}")
    public void refreshWatchlist() {
        jobs.run("watchlist", null, true);
    }

    @Scheduled(cron = "${radar.trends-cron:0 0 7 * * SUN}", zone = "${radar.time-zone:America/New_York}")
    public void rebuildTrends() {
        jobs.run("trends", null, true);
    }

    @Scheduled(cron = "${radar.digest-cron:0 0 8 * * MON}", zone = "${radar.time-zone:America/New_York}")
    public void sendDigest() {
        jobs.run("digest", null, true);
    }
}
