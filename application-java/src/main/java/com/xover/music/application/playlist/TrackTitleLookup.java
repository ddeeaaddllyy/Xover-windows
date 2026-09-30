package com.xover.music.application.playlist;

import java.net.URI;
import java.util.Optional;

/** Looks up a human-readable title without changing the source URL. */
@FunctionalInterface
public interface TrackTitleLookup {
    Optional<String> lookup(URI sourceUri) throws Exception;
}
