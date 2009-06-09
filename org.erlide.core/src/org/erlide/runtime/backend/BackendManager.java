/*******************************************************************************
 * Copyright (c) 2009 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available
 * at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.runtime.backend;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunch;
import org.erlide.jinterface.backend.Backend;
import org.erlide.jinterface.backend.BackendException;
import org.erlide.jinterface.backend.BackendListener;
import org.erlide.jinterface.backend.BackendNode;
import org.erlide.jinterface.backend.RuntimeInfo;
import org.erlide.jinterface.util.EpmdWatcher;
import org.osgi.framework.Bundle;

public interface BackendManager {

	public static final String DEFAULT_VERSION = "R12B";
	public static final String[] SUPPORTED_MAIN_VERSIONS = new String[] { "",
			"R11B", "R12B", "R13B" };
	public static final String[] SUPPORTED_VERSIONS = new String[] { "",
			"R11B-3", "R11B-4", "R11B-5", "R12B-1", "R12B-2", "R12B-3",
			"R12B-4", "R12B-5", "R13B" };

	public enum BackendEvent {
		ADDED, REMOVED
	};

	public enum BackendOptions {
		DEBUG, AUTOSTART, TRAP_EXIT
	};

	public ErlideBackend create(final RuntimeInfo info,
			final Set<BackendOptions> options, final ILaunch launch)
			throws BackendException;

	public Backend getBuildBackend(final IProject project)
			throws BackendException;

	public Set<ErlideBackend> getExecutionBackends(final IProject project);

	public ErlideBackend getIdeBackend();

	public Collection<ErlideBackend> getAllBackends();

	public void addExecutionBackend(final IProject project,
			final ErlideBackend b);

	public void removeExecutionBackend(final IProject project, final BackendNode b);

	public void forEachBackend(final ErlideBackendVisitor visitor);

	public void addBackendListener(final BackendListener listener);

	public void removeBackendListener(final BackendListener listener);

	public void addBundle(final Bundle p);

	public void removeBundle(final Bundle b);

	public void updateNodeStatus(final String host, final List<String> started,
			final List<String> stopped);

	public EpmdWatcher getEpmdWatcher();

	public void remoteNodeStatus(final String node, final boolean up,
			final Object info);

	public void dispose(final BackendNode backend);

	public void remoteStatus(final String node, final boolean up,
			final Object info);

	public void connAttempt(final String node, final boolean incoming,
			final Object info);

}