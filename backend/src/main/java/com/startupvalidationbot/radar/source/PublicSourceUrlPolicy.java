package com.startupvalidationbot.radar.source;

import java.net.InetAddress;
import java.net.URI;

public final class PublicSourceUrlPolicy {
    private PublicSourceUrlPolicy() {
    }

    public static URI requirePublicHttpUrl(String value) throws SourceFetchException {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new SourceFetchException("Source URL must be a public HTTP(S) URL without credentials");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new SourceFetchException("Source URL resolves to a private or local network address");
                }
            }
            return uri;
        } catch (SourceFetchException error) {
            throw error;
        } catch (Exception error) {
            throw new SourceFetchException("Source URL is invalid or cannot be resolved", error);
        }
    }
}
