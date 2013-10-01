package org.erlide.core.internal.builder;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.IProgressMonitor;
import org.erlide.core.builder.IBuilder;
import org.erlide.util.ErlLogger;

public class RebarBuilder implements IBuilder {

    private final IProject project;

    public RebarBuilder(final IProject project) {
        this.project = project;
    }

    @Override
    public IProject[] build(final int kind, final Map<String, String> args,
            final IResourceDelta delta, final IProgressMonitor monitor) {
        ErlLogger.debug("RebarBuilder: not implemented yet");
        return null;
    }

    @Override
    public void clean(final IProgressMonitor monitor) {
        ErlLogger.debug("RebarBuilder: not implemented yet");
    }

}
