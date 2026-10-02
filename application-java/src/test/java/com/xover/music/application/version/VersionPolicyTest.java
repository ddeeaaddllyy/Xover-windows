package com.xover.music.application.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VersionPolicyTest {
    @Test
    void offersUpdateWhenPublishedMajorVersionIncreases() {
        assertTrue(VersionPolicy.shouldOfferUpdate("1.0.0", new RemoteVersions("1.0.0", "2.0.0")));
        assertTrue(VersionPolicy.shouldOfferUpdate("1.9.9", new RemoteVersions("1.9.9", "3.0.0")));
    }

    @Test
    void doesNotOfferUpdateForMinorPatchEqualOrOlderVersions() {
        assertFalse(VersionPolicy.shouldOfferUpdate("1.0.0", new RemoteVersions("1.0.0", "1.1.0")));
        assertFalse(VersionPolicy.shouldOfferUpdate("1.0.0", new RemoteVersions("1.0.0", "1.0.1")));
        assertFalse(VersionPolicy.shouldOfferUpdate("2.0.0", new RemoteVersions("2.0.0", "2.0.0")));
        assertFalse(VersionPolicy.shouldOfferUpdate("2.0.0", new RemoteVersions("2.0.0", "1.9.9")));
    }

    @Test
    void acceptsReleaseSuffixAndBuildMetadata() {
        assertTrue(VersionPolicy.shouldOfferUpdate("1.0.0+windows", new RemoteVersions("1.0.0", "2.0.0-rc1")));
    }
}
