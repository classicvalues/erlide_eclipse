package org.erlide.backend;

import org.eclipse.core.resources.IProject;
import org.erlide.backend.internal.BackendFactory;
import org.erlide.backend.internal.BackendManager;
import org.erlide.backend.runtimeinfo.RuntimeInfoPreferencesSerializer;
import org.erlide.runtime.runtimeinfo.RuntimeInfo;
import org.erlide.runtime.runtimeinfo.RuntimeInfoManager;
import org.erlide.runtime.runtimeinfo.RuntimeInfoManagerData;

public class BackendCore {

    private static RuntimeInfoManager runtimeInfoManager;
    private static IBackendManager backendManager;
    private static BackendFactory backendFactory;

    public static final RuntimeInfoManager getRuntimeInfoManager() {
        if (runtimeInfoManager == null) {
            final RuntimeInfoPreferencesSerializer serializer = new RuntimeInfoPreferencesSerializer();
            final RuntimeInfoManagerData data = serializer.load();

            runtimeInfoManager = new RuntimeInfoManager();
            runtimeInfoManager.setRuntimes(data.runtimes,
                    data.defaultRuntimeName, data.erlideRuntimeName);
        }
        return runtimeInfoManager;
    }

    public static final IBackendManager getBackendManager() {
        if (backendManager == null) {
            final RuntimeInfo erlideRuntime = getRuntimeInfoManager()
                    .getErlideRuntime();
            backendFactory = new BackendFactory(getRuntimeInfoManager());
            backendManager = new BackendManager(erlideRuntime, backendFactory);
        }
        return backendManager;
    }

    public static IBackend getBuildOrIdeBackend(final IProject project) {
        final IBackendManager backendManager = getBackendManager();
        if (project != null) {
            try {
                return backendManager.getBuildBackend(project);
            } catch (final BackendException e) {
            }
        }
        return backendManager.getIdeBackend();
    }

}
