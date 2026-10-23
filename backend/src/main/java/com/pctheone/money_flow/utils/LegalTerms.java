package com.pctheone.money_flow.utils;

//---- Version of the terms of use / privacy policy currently published at /terms and /privacy.
//---- Must match TERMS_VERSION in frontend/src/lib/legal.ts. Bumping it asks every owner to accept again on the next login.
public final class LegalTerms {

    public static final String CURRENT_VERSION = "1.0";

    private LegalTerms() {
    }

    public static boolean isCurrent(String version) {
        return CURRENT_VERSION.equals(version);
    }
}
