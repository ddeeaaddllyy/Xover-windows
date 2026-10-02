package com.xover.music.application.version;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compares release versions and decides when to offer a major update. */
public final class VersionPolicy {
    private static final Pattern VERSION = Pattern.compile(
        "^([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$"
    );

    private VersionPolicy() {
    }

    public static boolean shouldOfferUpdate(String installedVersion, RemoteVersions published) {
        Objects.requireNonNull(published, "published");
        Version installed = parse(installedVersion);
        Version latest = parse(published.latest());
        return latest.major.compareTo(installed.major) > 0 && latest.compareTo(installed) > 0;
    }

    private static Version parse(String text) {
        Matcher match = VERSION.matcher(Objects.requireNonNull(text, "version"));
        if (!match.matches()) {
            throw new IllegalArgumentException("Invalid version: " + text);
        }
        return new Version(
            new BigInteger(match.group(1)),
            new BigInteger(match.group(2)),
            new BigInteger(match.group(3)),
            match.group(4)
        );
    }

    private record Version(BigInteger major, BigInteger minor, BigInteger patch, String prerelease)
        implements Comparable<Version> {
        @Override
        public int compareTo(Version other) {
            int result = major.compareTo(other.major);
            if (result != 0) return result;
            result = minor.compareTo(other.minor);
            if (result != 0) return result;
            result = patch.compareTo(other.patch);
            if (result != 0) return result;
            if (prerelease == null) return other.prerelease == null ? 0 : 1;
            if (other.prerelease == null) return -1;
            String[] left = prerelease.split("\\.");
            String[] right = other.prerelease.split("\\.");
            for (int i = 0; i < Math.min(left.length, right.length); i++) {
                boolean leftNumeric = left[i].chars().allMatch(Character::isDigit);
                boolean rightNumeric = right[i].chars().allMatch(Character::isDigit);
                if (leftNumeric && rightNumeric) {
                    result = new BigInteger(left[i]).compareTo(new BigInteger(right[i]));
                } else if (leftNumeric != rightNumeric) {
                    result = leftNumeric ? -1 : 1;
                } else {
                    result = left[i].compareTo(right[i]);
                }
                if (result != 0) return result;
            }
            return Integer.compare(left.length, right.length);
        }
    }
}
