/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.erlide.ui.actions;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.erlide.backend.BackendException;
import org.erlide.backend.rpc.RpcCallSite;
import org.erlide.core.erlang.ErlModelException;
import org.erlide.core.erlang.ErlangCore;
import org.erlide.core.erlang.IErlElement;
import org.erlide.core.erlang.IErlElement.Kind;
import org.erlide.core.erlang.IErlFunction;
import org.erlide.core.erlang.IErlImport;
import org.erlide.core.erlang.IErlModel;
import org.erlide.core.erlang.IErlModule;
import org.erlide.core.erlang.IErlProject;
import org.erlide.core.erlang.IErlRecordDef;
import org.erlide.core.erlang.IParent;
import org.erlide.core.erlang.ISourceRange;
import org.erlide.core.erlang.ISourceReference;
import org.erlide.core.erlang.util.ErlangFunction;
import org.erlide.core.erlang.util.ModelUtils;
import org.erlide.core.text.ErlangToolkit;
import org.erlide.jinterface.util.ErlLogger;
import org.erlide.ui.editors.erl.ErlangEditor;
import org.erlide.ui.prefs.plugin.NavigationPreferencePage;
import org.erlide.ui.util.ErlModelUtils;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangRangeException;
import com.ericsson.otp.erlang.OtpErlangString;

import erlang.ErlideOpen;
import erlang.OpenResult;

/**
 * This action opens a Erlang editor on a Erlang element or file.
 * <p>
 * The action is applicable to selections containing elements of type
 * <code>ICompilationUnit</code>, <code>IMember</code> or <code>IFile</code>.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class OpenAction extends SelectionDispatchAction {

    /**
     * Creates a new <code>OpenAction</code>. The action requires that the
     * selection provided by the site's selection provider is of type <code>
     * org.eclipse.jface.viewers.IStructuredSelection</code> .
     * 
     * @param site
     *            the site providing context information for this action
     * @param externalModules
     *            the externalModules file that can be searched for references
     *            to external modules
     */
    public OpenAction(final IWorkbenchSite site) {
        super(site);
        setText(ActionMessages.OpenAction_label);
        setToolTipText(ActionMessages.OpenAction_tooltip);
        setDescription(ActionMessages.OpenAction_description);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(this, "erl.open");

    }

    public OpenAction(final ErlangEditor erlangEditor) {
        super(erlangEditor.getSite());
        setText(ActionMessages.OpenAction_open_declaration_label);
        setToolTipText(ActionMessages.OpenAction_tooltip);
        setDescription(ActionMessages.OpenAction_description);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(this, "erl.open");
    }

    /*
     * (non-Javadoc) Method declared on SelectionDispatchAction.
     */
    @Override
    public void selectionChanged(final ITextSelection selection) {
    }

    /*
     * (non-Javadoc) Method declared on SelectionDispatchAction.
     */
    @Override
    public void selectionChanged(final IStructuredSelection selection) {
        setEnabled(checkEnabled(selection));
    }

    private boolean checkEnabled(final IStructuredSelection selection) {
        if (selection.isEmpty()) {
            return false;
        }
        for (final Object element : selection.toArray()) {
            if (element instanceof ISourceReference) {
                continue;
            }
            if (element instanceof IFile) {
                continue;
            }
            if (element instanceof IStorage) {
                continue;// FIXME We don't handle IStorage, do we?
            }
            if (element instanceof IErlModule) {
                continue;
            }
            return false;
        }
        return true;
    }

    /*
     * (non-Javadoc) Method declared on SelectionDispatchAction.
     */
    @Override
    public void run(final ITextSelection selection) {
        final ErlangEditor editor = (ErlangEditor) getSite().getPage()
                .getActiveEditor();
        editor.reconcileNow();
        final IErlModule module = editor.getModule();
        if (module == null) {
            return;
        }
        final RpcCallSite b = ErlangCore.getBackendManager().getIdeBackend();
        final int offset = selection.getOffset();
        try {
            final IErlProject erlProject = module.getErlProject();
            final IErlModel model = ErlangCore.getModel();
            final OpenResult res = ErlideOpen.open(b,
                    ErlangToolkit.createScannerModuleName(module), offset,
                    ModelUtils.getImportsAsList(module),
                    erlProject.getExternalModulesString(), model.getPathVars());
            ErlLogger.debug("open " + res);
            openOpenResult(editor, module, b, offset, erlProject, res);
        } catch (final Exception e) {
            ErlLogger.warn(e);
        }
    }

    /*
     * (non-Javadoc) Method declared on SelectionDispatchAction.
     */
    @Override
    public void run(final IStructuredSelection selection) {
        if (!checkEnabled(selection)) {
            return;
        }
        for (final Object i : selection.toArray()) {
            if (i instanceof IErlElement) {
                try {
                    ErlModelUtils.openElement((IErlElement) i);
                } catch (final CoreException e) {
                    ErlLogger.error(e);
                }
            }
        }
    }

    public static void openOpenResult(final ErlangEditor editor,
            final IErlModule module, final RpcCallSite backend,
            final int offset, final IErlProject erlProject, final OpenResult res)
            throws CoreException, ErlModelException, PartInitException,
            BadLocationException, OtpErlangRangeException, BackendException {
        final Object found = findOpenResult(editor, module, backend,
                erlProject, res, offset);
        if (found instanceof IErlElement) {
            ErlModelUtils.openElement((IErlElement) found);
        } else if (found instanceof ISourceRange) {
            ErlModelUtils.openSourceRange(module, (ISourceRange) found);
        }
    }

    public static Object findOpenResult(final ErlangEditor editor,
            final IErlModule module, final RpcCallSite backend,
            final IErlProject erlProject, final OpenResult res, final int offset)
            throws CoreException, BackendException, ErlModelException,
            BadLocationException, OtpErlangRangeException {
        final IErlElement element = editor.getElementAt(offset, true);
        final boolean checkAllProjects = NavigationPreferencePage
                .getCheckAllProjects();
        final IErlModel model = ErlangCore.getModel();
        Object found = null;
        if (res.isExternalCall()) {
            found = findExternalCallOrType(module, res, erlProject, element,
                    checkAllProjects);
        } else if (res.isInclude()) {
            found = findInclude(module, erlProject, res, model);
        } else if (res.isLocalCall()) {
            found = findLocalCall(module, backend, erlProject, res, element,
                    checkAllProjects);
        } else if (res.isVariable() && element instanceof ISourceReference) {
            final ISourceReference sref = (ISourceReference) element;
            final ISourceRange range = sref.getSourceRange();
            final String elementText = editor.getDocument().get(
                    range.getOffset(), range.getLength());
            found = ModelUtils.findVariable(backend, range, res.getName(),
                    elementText);
        } else if (res.isRecord() || res.isMacro()) {
            final Kind kind = res.isMacro() ? Kind.MACRO_DEF : Kind.RECORD_DEF;
            found = ModelUtils.findPreprocessorDef(module, res.getName(), kind);
        } else if (res.isField()) {
            final IErlRecordDef def = (IErlRecordDef) ModelUtils
                    .findPreprocessorDef(module, res.getFun(), Kind.RECORD_DEF);
            if (def != null) {
                found = def.getFieldNamed(res.getName());
            }
        }
        return found;
    }

    private static IErlElement findInclude(final IErlModule module,
            final IErlProject project, final OpenResult res,
            final IErlModel model) throws CoreException, BackendException {
        if (project != null) {
            final IErlModule include = project.findInclude(res.getName(),
                    res.getPath(), true, false);
            if (include != null) {
                return include;
            }
            final IParent parent = module.getParent();
            return parent.getChildNamed(res.getName());
        }
        return null;
    }

    public static boolean isTypeDefOrRecordDef(final IErlElement element) {
        return element != null
                && (element.getKind() == IErlElement.Kind.TYPESPEC || element
                        .getKind() == IErlElement.Kind.RECORD_DEF);
    }

    private static IErlElement findLocalCall(final IErlModule module,
            final RpcCallSite backend, final IErlProject erlProject,
            final OpenResult res, final IErlElement element,
            final boolean checkAllProjects) throws BackendException,
            CoreException {
        if (isTypeDefOrRecordDef(element)) {
            return ModelUtils.findTypespec(module, res.getFun());
        }
        final IErlFunction foundElement = module
                .findFunction(res.getFunction());
        if (foundElement != null) {
            return foundElement;
        }
        // not local imports
        OtpErlangObject res2 = null;
        String moduleName = null;
        if (module != null) {
            final IErlImport ei = module.findImport(res.getFunction());
            if (ei != null) {
                final IErlModel model = ErlangCore.getModel();
                moduleName = ei.getImportModule();
                res2 = ErlideOpen.getSourceFromModule(backend,
                        model.getPathVars(), moduleName,
                        erlProject.getExternalModulesString());
            }
        }
        if (res2 instanceof OtpErlangString && moduleName != null) {
            final OtpErlangString otpErlangString = (OtpErlangString) res2;
            final String modulePath = otpErlangString.stringValue();
            return ModelUtils.findFunction(moduleName, res.getFunction(),
                    modulePath, erlProject, checkAllProjects, module);
        } else {
            return null;
        }
    }

    private static IErlElement findExternalCallOrType(final IErlModule module,
            final OpenResult res, final IErlProject project,
            final IErlElement element, final boolean checkAllProjects)
            throws CoreException {
        if (isTypeDefOrRecordDef(element)) {
            return ModelUtils.findTypeDef(module, res.getName(), res.getFun(),
                    res.getPath(), project, checkAllProjects);
        }
        final IErlElement result = ModelUtils.findFunction(res.getName(),
                res.getFunction(), res.getPath(), project, checkAllProjects,
                module);
        if (result instanceof IErlFunction) {
            return result;
        }
        return ModelUtils.findFunction(res.getName(),
                new ErlangFunction(res.getFun(), ErlangFunction.ANY_ARITY),
                res.getPath(), project, checkAllProjects, module);
    }

}
