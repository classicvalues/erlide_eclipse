/*******************************************************************************
 * Copyright (c) 2005 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.core.model.erlang.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.erlide.core.ErlangCore;
import org.erlide.core.common.CommonUtils;
import org.erlide.core.model.erlang.ErlModelException;
import org.erlide.core.model.erlang.IErlAttribute;
import org.erlide.core.model.erlang.IErlComment;
import org.erlide.core.model.erlang.IErlElement;
import org.erlide.core.model.erlang.IErlExport;
import org.erlide.core.model.erlang.IErlExternal;
import org.erlide.core.model.erlang.IErlFolder;
import org.erlide.core.model.erlang.IErlFunction;
import org.erlide.core.model.erlang.IErlImport;
import org.erlide.core.model.erlang.IErlModel;
import org.erlide.core.model.erlang.IErlModule;
import org.erlide.core.model.erlang.IErlPreprocessorDef;
import org.erlide.core.model.erlang.IErlProject;
import org.erlide.core.model.erlang.IErlProject.Scope;
import org.erlide.core.model.erlang.IErlTypespec;
import org.erlide.core.model.erlang.IErlangFirstThat;
import org.erlide.core.model.erlang.IParent;
import org.erlide.core.model.erlang.ISourceRange;
import org.erlide.core.model.erlang.ISourceReference;
import org.erlide.core.model.erlang.ModuleKind;
import org.erlide.core.model.erlang.util.ErlangFunction;
import org.erlide.core.model.erlang.util.ErlangIncludeFile;
import org.erlide.core.services.text.ErlScanner;
import org.erlide.core.services.text.ErlToken;
import org.erlide.core.services.text.ErlangToolkit;
import org.erlide.jinterface.ErlLogger;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangString;
import com.google.common.collect.Lists;

public class ErlModule extends Openable implements IErlModule {

    private long timestamp = IResource.NULL_STAMP;
    private IFile fFile;
    private final ModuleKind moduleKind;
    protected String path;
    private String initialText;
    private boolean parsed;
    private final String scannerName;
    private ErlScanner scanner;
    private final boolean useCaches;
    private final Collection<IErlComment> comments;

    protected ErlModule(final IParent parent, final String name,
            final String initialText, final IFile file, final String path,
            final boolean useCaches) {
        super(parent, name);
        fFile = file;
        moduleKind = ModuleKind.nameToModuleKind(name);
        this.path = path;
        this.initialText = initialText;
        parsed = false;
        scannerName = ErlangToolkit.createScannerModuleName(this);
        scanner = null;
        this.useCaches = useCaches;
        comments = Lists.newArrayList();
        if (ErlModelManager.verbose) {
            final IErlElement element = (IErlElement) parent;
            final String parentName = element.getName();
            ErlLogger.debug("...creating " + parentName + "/" + getName() + " "
                    + moduleKind);
        }
        getModelCache().putModule(this);
    }

    public boolean internalBuildStructure(final IProgressMonitor pm) {
        if (scanner == null) {
            parsed = false;
        }
        final boolean initialParse = !parsed;
        if (scanner == null) {
            // There are two places that we make the initial scanner... this
            // is one
            getScanner();
        }
        parsed = ErlParser.parse(this, scannerName, initialParse, path,
                useCaches);
        getScanner();
        disposeScanner();
        return parsed;
    }

    @Override
    protected synchronized boolean buildStructure(final IProgressMonitor pm)
            throws ErlModelException {
        if (internalBuildStructure(pm)) {
            final IErlModel model = ErlangCore.getModel();
            if (model != null) {
                model.notifyChange(this);
            }
            final IResource r = getResource();
            if (r instanceof IFile) {
                timestamp = ((IFile) r).getLocalTimeStamp();
            } else {
                timestamp = IResource.NULL_STAMP;
            }
            return true;
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.erlide.core.model.erlang.internal.ErlElement#getFilePath()
     */
    @Override
    public String getFilePath() {
        if (path != null) {
            return path;
        }
        final IPath location = fFile.getLocation();
        if (location == null) {
            return null;
        }
        return location.toString();
    }

    public IErlElement getElementAt(final int position)
            throws ErlModelException {
        return getModel().innermostThat(this, new IErlangFirstThat() {
            public boolean firstThat(final IErlElement e) {
                try {
                    if (e instanceof ISourceReference) {
                        final ISourceReference ch = (ISourceReference) e;
                        ISourceRange r;
                        r = ch.getSourceRange();
                        if (r != null && r.hasPosition(position)) {
                            return true;
                        }
                    }
                } catch (final ErlModelException e1) {
                    ErlLogger.error(e1);
                }
                return false;
            }
        });
    }

    public IErlElement getElementAtLine(final int lineNumber) {
        return getModel().innermostThat(this, new IErlangFirstThat() {
            public boolean firstThat(final IErlElement e) {
                if (e instanceof ISourceReference) {
                    final ISourceReference sr = (ISourceReference) e;
                    if (sr.getLineStart() <= lineNumber
                            && sr.getLineEnd() >= lineNumber) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public ModuleKind getModuleKind() {
        return moduleKind;
    }

    @Override
    public IResource getResource() {
        return getCorrespondingResource();
    }

    @Override
    public IResource getCorrespondingResource() {
        return fFile;
    }

    public ISourceRange getSourceRange() throws ErlModelException {
        return new SourceRange(0, 0);
    }

    public void copy(final IErlElement container, final IErlElement sibling,
            final String rename, final boolean replace,
            final IProgressMonitor monitor) throws ErlModelException {
        // TODO Auto-generated method stub

    }

    public void delete(final boolean force, final IProgressMonitor monitor)
            throws ErlModelException {
        // TODO Auto-generated method stub

    }

    public void move(final IErlElement container, final IErlElement sibling,
            final String rename, final boolean replace,
            final IProgressMonitor monitor) throws ErlModelException {
        // TODO Auto-generated method stub

    }

    public void rename(final String aname, final boolean replace,
            final IProgressMonitor monitor) throws ErlModelException {
        // TODO Auto-generated method stub

    }

    @Override
    protected boolean hasBuffer() {
        return true;
    }

    public void addComment(final IErlComment c) {
        comments.add(c);
    }

    public Collection<IErlComment> getComments() {
        return Collections.unmodifiableCollection(comments);
    }

    @Override
    public void removeChildren() {
        super.removeChildren();
        comments.clear();
    }

    public synchronized long getTimestamp() {
        return timestamp;
    }

    public IErlImport findImport(final ErlangFunction function) {
        try {
            for (final IErlElement e : getChildrenOfKind(Kind.IMPORT)) {
                if (e instanceof IErlImport) {
                    final IErlImport ei = (IErlImport) e;
                    if (ei.hasFunction(function)) {
                        return ei;
                    }
                }
            }
        } catch (final ErlModelException e) {
        }
        return null;
    }

    public IErlExport findExport(final ErlangFunction function) {
        try {
            for (final IErlElement e : getChildrenOfKind(Kind.EXPORT)) {
                if (e instanceof IErlExport) {
                    final IErlExport ee = (IErlExport) e;
                    if (ee.hasFunction(function)) {
                        return ee;
                    }
                }
            }
        } catch (final ErlModelException e) {
        }
        return null;
    }

    public IErlFunction findFunction(final ErlangFunction function) {
        for (final IErlElement fun : internalGetChildren()) {
            if (fun instanceof IErlFunction) {
                final IErlFunction f = (IErlFunction) fun;
                if (f.getName().equals(function.name)
                        && (function.arity < 0 || f.getArity() == function.arity)) {
                    return f;
                }
            }
        }
        return null;
    }

    public IErlTypespec findTypespec(final String typeName) {
        for (final IErlElement child : internalGetChildren()) {
            if (child instanceof IErlTypespec) {
                final IErlTypespec typespec = (IErlTypespec) child;
                if (typespec.getName().equals(typeName)) {
                    return typespec;
                }
            }
        }
        return null;
    }

    public IErlPreprocessorDef findPreprocessorDef(final String definedName,
            final Kind kind) {
        try {
            for (final IErlElement m : getChildren()) {
                if (m instanceof IErlPreprocessorDef) {
                    final IErlPreprocessorDef pd = (IErlPreprocessorDef) m;
                    if (pd.getKind() == kind
                            && pd.getDefinedName().equals(definedName)) {
                        return pd;
                    }
                }
            }
        } catch (final ErlModelException e) {
        }
        return null;
    }

    public Collection<ErlangIncludeFile> getIncludedFiles()
            throws ErlModelException {
        if (!isStructureKnown()) {
            open(null);
        }
        final List<ErlangIncludeFile> r = new ArrayList<ErlangIncludeFile>(0);
        for (final IErlElement m : getChildren()) {
            if (m instanceof IErlAttribute) {
                final IErlAttribute a = (IErlAttribute) m;
                final OtpErlangObject v = a.getValue();
                if (v instanceof OtpErlangString) {
                    final String s = ((OtpErlangString) v).stringValue();
                    if ("include".equals(a.getName())) {
                        r.add(new ErlangIncludeFile(false, s));
                    } else if ("include_lib".equals(a.getName())) {
                        r.add(new ErlangIncludeFile(true, s));
                    }
                }
            }
        }
        return r;
    }

    public Collection<IErlImport> getImports() {
        final List<IErlImport> result = new ArrayList<IErlImport>();
        try {
            for (final IErlElement e : getChildren()) {
                if (e instanceof IErlImport) {
                    final IErlImport ei = (IErlImport) e;
                    result.add(ei);
                }
            }
        } catch (final ErlModelException e) {
        }
        return result;
    }

    public synchronized void reconcileText(final int offset,
            final int removeLength, final String newText,
            final IProgressMonitor mon) {
        if (scanner == null) {
            // There are two places that we make the initial scanner... this
            // is one too
            getScanner();
        }
        getScanner();
        if (scanner != null) {
            scanner.replaceText(offset, removeLength, newText);
        }
        if (mon != null) {
            mon.worked(1);
        }
        setStructureKnown(false);
        disposeScanner();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.erlide.core.model.erlang.IErlModule#postReconcile(org.eclipse.core
     * .runtime .IProgressMonitor)
     */
    public synchronized void postReconcile(final IProgressMonitor mon) {
        try {
            open(mon);
        } catch (final ErlModelException e) {
            ErlLogger.warn(e);
        }
        if (mon != null) {
            mon.worked(1);
        }
    }

    @Override
    protected void closing(final Object info) throws ErlModelException {
        // TODO Auto-generated method stub

    }

    public synchronized void initialReconcile() {
        // currently unused
        // Note that the ErlReconciler doesn't send the first full-text
        // reconcile that the built-in reconciler does
    }

    public synchronized void finalReconcile() {
        // currently unused
    }

    @Override
    public String getModuleName() {
        return CommonUtils.withoutExtension(getName());
    }

    public void disposeScanner() {
        if (scanner == null) {
            return;
        }
        final ErlScanner s = scanner;
        if (s.willDispose()) {
            scanner = null;
        }
        s.dispose();
        setStructureKnown(false);
    }

    // public synchronized void disposeParser() {
    // final Backend b = ErlangCore.getBackendManager().getIdeBackend();
    // ErlideNoparse.destroy(b, getModuleName());
    // disposeScanner();
    // setStructureKnown(false);
    // parsed = false;
    // }

    public Kind getKind() {
        return Kind.MODULE;
    }

    @Override
    public void dispose() {
        disposeScanner();
        ErlangCore.getModelManager().removeModule(this);
    }

    public Set<IErlModule> getDirectDependents() throws ErlModelException {
        final Set<IErlModule> result = new HashSet<IErlModule>();
        final IErlProject project = getProject();
        for (final IErlModule m : project.getModules()) {
            ErlLogger.debug(m.toString());
            final boolean wasOpen = m.isOpen();
            if (!wasOpen) {
                m.open(null);
            }
            final Collection<ErlangIncludeFile> incs = m.getIncludedFiles();
            for (final ErlangIncludeFile inc : incs) {
                if (inc.getFilename().equals(getName())) {
                    result.add(m);
                    break;
                }
            }
            if (!wasOpen) {
                m.close();
            }
        }
        return result;
    }

    public Set<IErlModule> getAllDependents() throws ErlModelException {
        final Set<IErlModule> mod = getDirectDependents();
        return getAllDependents(mod, new HashSet<IErlModule>());
    }

    private Set<IErlModule> getAllDependents(final Set<IErlModule> current,
            final Set<IErlModule> result) throws ErlModelException {
        final Set<IErlModule> next = new HashSet<IErlModule>();
        for (final IErlModule mod : current) {
            final Collection<IErlModule> dep = mod.getDirectDependents();
            result.add(mod);
            next.addAll(dep);
        }
        if (next.size() == 0) {
            return result;

        } else {
            return getAllDependents(next, result);
        }
    }

    public synchronized void resetAndCacheScannerAndParser(final String newText)
            throws ErlModelException {
        while (scanner != null) {
            disposeScanner();
        }
        initialText = newText;
        parsed = false;
        setStructureKnown(false);
        final boolean built = buildStructure(null);
        setStructureKnown(built);
    }

    public ErlToken getScannerTokenAt(final int offset) {
        if (scanner != null) {
            return scanner.getTokenAt(offset);
        }
        return null;
    }

    public void setResource(final IFile file) {
        fFile = file;
    }

    @Override
    public String getLabelString() {
        return getName();
    }

    public void getScanner() {
        if (scanner == null) {
            scanner = getNewScanner();
        }
        scanner.addRef();
    }

    private ErlScanner getNewScanner() {
        if (path == null) {
            return null;
        }
        if (initialText == null) {
            initialText = "";
        }
        return new ErlScanner(scannerName, initialText, path, useCaches);
    }

    public Collection<IErlPreprocessorDef> getPreprocessorDefs(final Kind kind) {
        final List<IErlPreprocessorDef> result = Lists.newArrayList();
        try {
            for (final IErlElement e : getChildren()) {
                if (e instanceof IErlPreprocessorDef) {
                    final IErlPreprocessorDef pd = (IErlPreprocessorDef) e;
                    if (pd.getKind() == kind || kind == Kind.ERROR) {
                        result.add(pd);
                    }
                }
            }
        } catch (final ErlModelException e) {
        }
        return result;
    }

    public String getInitialText() {
        return initialText;
    }

    public List<IErlModule> findAllIncludedFiles() throws CoreException {
        final List<IErlModule> checked = Lists.newArrayList();
        checked.add(this);
        return findAllIncludedFiles(checked);
    }

    public List<IErlModule> findAllIncludedFiles(final List<IErlModule> checked)
            throws CoreException {
        final List<IErlModule> includedFilesForModule = ErlModel
                .getErlModelCache().getIncludedFilesForModule(this);
        if (includedFilesForModule != null && !includedFilesForModule.isEmpty()) {
            return includedFilesForModule;
        }
        final List<IErlModule> result = Lists.newArrayList();
        final Collection<ErlangIncludeFile> includedFiles = getIncludedFiles();
        final IErlProject project = getProject();
        if (project == null) {
            return result;
        }
        final Collection<IErlModule> includes = project.getIncludes();
        includes.addAll(getLocalIncludes());
        Collection<IErlModule> externalIncludes = null;
        Collection<IErlModule> referencedIncludes = null;
        Collection<IErlModule> modules = null;
        for (final ErlangIncludeFile includeFile : includedFiles) {
            final String includeFileName = includeFile.getFilenameLastPart();
            if (findAllIncludedFilesAux(checked, result, includes,
                    includeFileName)) {
                continue;
            }
            if (referencedIncludes == null) {
                referencedIncludes = Lists.newArrayList();
                final Collection<IErlProject> referencedProjects = project
                        .getReferencedProjects();
                for (final IErlProject referencedProject : referencedProjects) {
                    referencedIncludes.addAll(referencedProject.getIncludes());
                }
            }
            if (findAllIncludedFilesAux(checked, result, referencedIncludes,
                    includeFileName)) {
                continue;
            }
            if (externalIncludes == null) {
                externalIncludes = project.getExternalIncludes();
            }
            if (findAllIncludedFilesAux(checked, result, externalIncludes,
                    includeFileName)) {
                continue;
            }
            if (modules == null) {
                modules = project.getModules();
            }
            findAllIncludedFilesAux(checked, result, modules, includeFileName);
        }
        ErlModel.getErlModelCache().putIncludedFilesForModule(this, result);
        return result;
    }

    private Collection<IErlModule> getLocalIncludes() throws ErlModelException {
        final List<IErlModule> result = Lists.newArrayList();
        final IParent parent = getParent();
        for (final IErlElement child : parent.getChildrenOfKind(Kind.MODULE)) {
            if (child instanceof IErlModule
                    && ModuleKind.nameToModuleKind(child.getName()) == ModuleKind.HRL) {
                result.add((IErlModule) child);
            }
        }
        return result;
    }

    private boolean findAllIncludedFilesAux(final List<IErlModule> checked,
            final List<IErlModule> result,
            final Collection<IErlModule> includes, final String includeFileName)
            throws CoreException {
        for (final IErlModule include : includes) {
            if (include.getName().equals(includeFileName)) {
                if (include.getParent() instanceof IErlExternal) {
                    result.add(findExternalIncludeInOpenProjects(include));
                } else {
                    result.add(include);
                }
                final ErlModule m = (ErlModule) include;
                result.addAll(m.findAllIncludedFiles(checked));
                return true;
            }
        }
        return false;
    }

    public static IErlModule findExternalIncludeInOpenProjects(
            final IErlModule externalInclude) throws CoreException {
        final String filePath = externalInclude.getFilePath();
        final Collection<IErlProject> projects = externalInclude.getModel()
                .getErlangProjects();
        for (final IErlProject project : projects) {
            final Collection<IErlModule> includes = project.getIncludes();
            for (final IErlModule include : includes) {
                if (include.getFilePath().equals(filePath)) {
                    return include;
                }
            }
        }
        return externalInclude;
    }

    public boolean isOnSourcePath() {
        final IParent parent = getParent();
        if (parent instanceof IErlFolder) {
            final IErlFolder folder = (IErlFolder) parent;
            return folder.isOnSourcePath();
        }
        if (checkPath(getProject().getSourceDirs())) {
            return true;
        }
        return false;
    }

    public boolean isOnIncludePath() {
        final IParent parent = getParent();
        if (parent instanceof IErlFolder) {
            final IErlFolder folder = (IErlFolder) parent;
            return folder.isOnIncludePath();
        }
        if (checkPath(getProject().getIncludeDirs())) {
            return true;
        }
        return false;
    }

    private boolean checkPath(final Collection<IPath> dirs) {
        final String thePath = getFilePath();
        if (thePath != null) {
            final IPath p = new Path(thePath).removeLastSegments(1);
            for (final IPath dir : dirs) {
                if (dir.equals(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    public IErlModule findInclude(final String includeName,
            final String includePath, final Scope scope)
            throws ErlModelException {
        final IParent parent = getParent();
        if (parent instanceof IErlFolder) {
            final IErlFolder folder = (IErlFolder) parent;
            folder.open(null);
            return folder.findModule(includeName, includePath);
        }
        return getProject().findInclude(includeName, includePath, scope);
    }

}
