package org.erlide.engine.services.search;

import com.ericsson.otp.erlang.OtpErlangObject;

public class TypeRefPattern extends ErlangSearchPattern {

    private final String module;

    @Override
    public String toString() {
        return "TypeRefPattern [module=" + module + ", name=" + name + ", limitTo="
                + limitTo + "]";
    }

    private final String name;

    public TypeRefPattern(final String module, final String name, final LimitTo limitTo) {
        super(limitTo);
        this.module = module;
        this.name = name;
    }

    @Override
    public OtpErlangObject getSearchObject() {
        return makeSSPatternObject(ErlangSearchPattern.TYPE_DEF_ATOM, ErlangSearchPattern.TYPE_REF_ATOM,
                module == null ? "_" : module, name);
    }

    @Override
    public String patternString() {
        if (module != null && !module.isEmpty()) {
            return module + ":" + name;
        }
        return name;
    }

    @Override
    public SearchFor getSearchFor() {
        return SearchFor.TYPE;
    }

    @Override
    public String labelString() {
        if (module != null && !module.isEmpty()) {
            return module + ":" + name;
        }
        return name;
    }

}
