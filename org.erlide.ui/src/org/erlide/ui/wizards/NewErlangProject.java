/*******************************************************************************
 * Copyright (c) 2004 Eric Merritt and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eric Merritt
 *******************************************************************************/
package org.erlide.ui.wizards;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.erlide.core.ErlangPlugin;
import org.erlide.core.erlang.ErlangCore;
import org.erlide.core.erlang.IOldErlangProjectProperties;
import org.erlide.core.erlang.util.PluginUtils;
import org.erlide.core.preferences.OldErlangProjectProperties;
import org.erlide.jinterface.util.ErlLogger;
import org.erlide.ui.ErlideUIConstants;
import org.erlide.ui.ErlideUIPlugin;
import org.erlide.ui.perspectives.ErlangPerspective;

/**
 * Creates a new erlide project in the Eclipse workbench.
 * 
 * 
 * @author Eric Merritt [cyberlync at yahoo dot com]
 */
public class NewErlangProject extends Wizard implements INewWizard {

    /**
     * The main page on the wizard: collects the project name and location.
     */
    private WizardNewProjectCreationPage namePage;

    /**
     * The build preferences
     */
    private ProjectPreferencesWizardPage buildPage;

    /**
     * @see org.eclipse.ui.IWorkbenchWizard#init(org.eclipse.ui.IWorkbench,
     *      org.eclipse.jface.viewers.IStructuredSelection)
     */
    public void init(final IWorkbench workbench,
            final IStructuredSelection selection) {
        setNeedsProgressMonitor(true);
    }

    /**
     * @see org.eclipse.jface.wizard.IWizard#addPages()
     */
    @Override
    public void addPages() {
        try {
            super.addPages();
            namePage = new WizardNewProjectCreationPage(
                    "NewErlideProjectWizard");
            namePage.setTitle(ErlideUIPlugin
                    .getResourceString("wizards.titles.newproject"));
            namePage.setDescription(ErlideUIPlugin
                    .getResourceString("wizards.descs.newproject"));
            namePage.setImageDescriptor(ErlideUIPlugin.getDefault()
                    .getImageDescriptor(
                            ErlideUIConstants.IMG_NEW_PROJECT_WIZARD));
            addPage(namePage);

            buildPage = new ProjectPreferencesWizardPage(
                    "ProjectPreferencesWizardPage");
            buildPage.setTitle(ErlideUIPlugin
                    .getResourceString("wizards.titles.buildprefs"));
            buildPage.setDescription(ErlideUIPlugin
                    .getResourceString("wizards.descs.buildprefs"));
            buildPage.setImageDescriptor(ErlideUIPlugin.getDefault()
                    .getImageDescriptor(
                            ErlideUIConstants.IMG_NEW_PROJECT_WIZARD));
            addPage(buildPage);
        } catch (final Exception x) {
            reportError(x);
        }
    }

    /**
     * User has clicked "Finish", we create the project. In practice, it calls
     * the createProject() method in the appropriate thread.
     * 
     * @see org.eclipse.jface.wizard.IWizard#performFinish()
     */
    @Override
    public boolean performFinish() {
        if (!validateFinish()) {
            return false;
        }

        try {
            getContainer().run(false, true, new WorkspaceModifyOperation() {

                @Override
                protected void execute(final IProgressMonitor monitor) {
                    createProject(monitor != null ? monitor
                            : new NullProgressMonitor());

                    try {
                        final IWorkbench workbench = ErlideUIPlugin
                                .getDefault().getWorkbench();
                        workbench.showPerspective(ErlangPerspective.ID,
                                workbench.getActiveWorkbenchWindow());
                    } catch (final WorkbenchException we) {
                        // ignore
                    }
                }
            });
        } catch (final InvocationTargetException x) {
            reportError(x);
            return false;
        } catch (final InterruptedException x) {
            reportError(x);
            return false;
        }

        return true;
    }

    /**
     * Validate finish
     * 
     * @return
     */
    private boolean validateFinish() {
        ErlLogger.debug("validating |" + buildPage.getPrefs().getOutputDir()
                + "|");
        final IOldErlangProjectProperties prefs = buildPage.getPrefs();
        if (prefs.getOutputDir().isEmpty()) {
            reportError(ErlideUIPlugin
                    .getResourceString("wizard.errors.buildpath"));
            return false;
        }

        if (prefs.getSourceDirs().size() == 0) {
            reportError(ErlideUIPlugin
                    .getResourceString("wizards.errors.sourcepath"));
            return false;
        }
        return true;
    }

    /**
     * This is the actual implementation for project creation.
     * 
     * @param monitor
     *            reports progress on this object
     */
    @SuppressWarnings("serial")
    protected void createProject(final IProgressMonitor monitor) {
        monitor.beginTask(ErlideUIPlugin
                .getResourceString("wizards.messages.creatingproject"), 50);
        try {
            final IWorkspaceRoot root = ResourcesPlugin.getWorkspace()
                    .getRoot();
            monitor.subTask(ErlideUIPlugin
                    .getResourceString("wizards.messages.creatingdirectories"));
            final IProject project = root.getProject(namePage.getProjectName());
            IProjectDescription description = ResourcesPlugin.getWorkspace()
                    .newProjectDescription(project.getName());
            if (!Platform.getLocation().equals(namePage.getLocationPath())) {
                description.setLocation(namePage.getLocationPath());
            }
            project.create(description, monitor);
            monitor.worked(10);
            project.open(monitor);

            description = project.getDescription();

            description.setNatureIds(new String[] { ErlangPlugin.NATURE_ID });
            project.setDescription(description, new SubProgressMonitor(monitor,
                    10));

            monitor.worked(10);
            monitor.subTask(ErlideUIPlugin
                    .getResourceString("wizards.messages.creatingfiles"));

            final OldErlangProjectProperties bprefs = buildPage.getPrefs();

            buildPaths(monitor, root, project, new ArrayList<IPath>() {
                {
                    add(bprefs.getOutputDir());
                }
            });
            buildPaths(monitor, root, project, bprefs.getSourceDirs());
            buildPaths(monitor, root, project, bprefs.getIncludeDirs());

            final IOldErlangProjectProperties prefs = ErlangCore
                    .getProjectProperties(project);
            prefs.copyFrom(bprefs);
            final IEclipsePreferences node = new ProjectScope(project)
                    .getNode(ErlangPlugin.PLUGIN_ID);
            prefs.store(node);

            // TODO add code path to backend
            // final String out = project.getLocation().append(
            // prefs.getOutputDir()).toString();
        } catch (final CoreException x) {
            reportError(x);
        } finally {
            monitor.done();
        }
    }

    /**
     * Builds the path from the specified path list.
     * 
     * @param monitor
     *            The progress monitor to use
     * @param root
     *            the root worksapce
     * @param project
     *            the project
     * @param list
     *            the paths to create
     * @throws CoreException
     *             if a problem occures
     */
    private void buildPaths(final IProgressMonitor monitor,
            final IWorkspaceRoot root, final IProject project,
            final Collection<IPath> list) throws CoreException {
        // Some paths are optionals (include): If we do not specify it, we get a
        // null string and we do not need to create the directory
        if (list != null) {
            final IPath projectPath = project.getFullPath();
            for (final IPath pp : list) {
                // only create in-project paths
                if (!pp.isAbsolute() && !pp.toString().equals(".")
                        && !pp.isEmpty()) {
                    final IPath path = projectPath.append(pp);
                    final IFolder folder = root.getFolder(path);
                    createFolderHelper(folder, monitor);
                }
            }
        }
    }

    /**
     * Displays an error that occured during the project creation. *
     * 
     * @param x
     *            details on the error
     */
    private void reportError(final Exception x) {
        ErlLogger.error(x);
        ErrorDialog.openError(getShell(), ErlideUIPlugin
                .getResourceString("wizards.errors.projecterrordesc"),
                ErlideUIPlugin
                        .getResourceString("wizards.errors.projecterrortitle"),
                PluginUtils.makeStatus(x));
    }

    /**
     * Displays an error that occured during the project creation. *
     * 
     * @param x
     *            details on the error
     */
    private void reportError(final String x) {
        final Status status = new Status(IStatus.ERROR,
                ErlideUIPlugin.PLUGIN_ID, 0, x, null);

        ErrorDialog.openError(getShell(), x, ErlideUIPlugin
                .getResourceString("wizards.errors.projecterrortitle"), status);
    }

    /**
     * Helper method: it recursively creates a folder path.
     * 
     * @param folder
     * @param monitor
     * @throws CoreException
     * @see java.io.File#mkdirs()
     */
    private void createFolderHelper(final IFolder folder,
            final IProgressMonitor monitor) throws CoreException {
        if (!folder.exists()) {
            final IContainer parent = folder.getParent();
            if (parent instanceof IFolder && !((IFolder) parent).exists()) {
                createFolderHelper((IFolder) parent, monitor);
            }

            folder.create(false, true, monitor);
        }
    }

}
