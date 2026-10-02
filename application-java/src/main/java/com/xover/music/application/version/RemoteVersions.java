package com.xover.music.application.version;

import java.util.Objects;

/** Version values published in the repository's version.toml. */
public record RemoteVersions(String current, String latest) {
    public RemoteVersions {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(latest, "latest");
    }
}
