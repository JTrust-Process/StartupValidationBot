package com.startupvalidationbot.offering.intake;

import com.startupvalidationbot.offering.intake.NativeOfferingDomain.SourceResult;

public interface NativeOfferingSourceAdapter {
    String source();
    String capability();
    SourceResult discover(int maxCandidates, int maxDetailRequests);
}
