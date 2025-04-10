/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2002 - 2006 The ObjectStyle Group
 * and individual authors of the software.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        ObjectStyle Group (http://objectstyle.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "ObjectStyle Group" and "Cayenne"
 *    must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact andrus@objectstyle.org.
 *
 * 5. Products derived from this software may not be called "ObjectStyle"
 *    nor may "ObjectStyle" appear in their names without prior written
 *    permission of the ObjectStyle Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE OBJECTSTYLE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the ObjectStyle Group.  For more
 * information on the ObjectStyle Group, please see
 * <http://objectstyle.org/>.
 *
 */
package org.objectstyle.wolips.wizards;

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.objectstyle.wolips.baseforplugins.util.CharSetUtils;
import org.objectstyle.wolips.templateengine.ComponentEngine;

/**
 * @author mnolte
 * @author uli
 */
public class WOComponentCreator implements IRunnableWithProgress {
	private String componentName;

	private String packageName;

	private String superclassName;

	private boolean createBodyTag;

	private boolean createApiFile;

	private IResource parentResource;

	private WOComponentCreationPage page;

	private int htmlBodyType;

	private String wooEncoding;

	/**
	 * Constructor for WOComponentCreator.
	 *
	 * @param parentResource
	 * @param componentName
	 * @param packageName
	 * @param createBodyTag
	 * @param createApiFile
	 */
	public WOComponentCreator(IResource parentResource, String componentName, String packageName, String superclassName, boolean createBodyTag, boolean createApiFile, WOComponentCreationPage page) {
		this.parentResource = parentResource;
		this.componentName = componentName;
		this.packageName = packageName;
		this.superclassName = superclassName;
		this.createBodyTag = createBodyTag;
		this.createApiFile = createApiFile;
		this.page = page;
		this.htmlBodyType = page.getSelectedHTMLDocType().getTemplateIndex();
		this.wooEncoding = CharSetUtils.encodingNameFromObjectiveC(page.getSelectedEncoding());
	}

	@Override
	public void run(IProgressMonitor monitor) throws InvocationTargetException {
		try {
			createWOComponent(monitor);
		} catch (CoreException e) {
			throw new InvocationTargetException(e);
		}
	}

	/**
	 * Method createWOComponent.
	 *
	 * @param monitor
	 * @throws CoreException
	 * @throws InvocationTargetException
	 */
	private void createWOComponent(IProgressMonitor monitor) throws CoreException, InvocationTargetException {
		IFolder componentFolder = null;
		IPath apiPath = null;
		IContainer componentFolderToReveal = null;

		switch (this.parentResource.getType()) {
		case IResource.PROJECT:
			componentFolder = ((IProject) this.parentResource).getFolder(this.componentName + ".wo");
			componentFolderToReveal = componentFolder.getParent();
			apiPath = this.parentResource.getProject().getLocation();
			break;
		case IResource.FOLDER:
			componentFolder = ((IFolder) this.parentResource).getFolder(this.componentName + ".wo");
			componentFolderToReveal = componentFolder.getParent();
			apiPath = componentFolder.getParent().getLocation();
//			IFolder pbFolder = project.getParentFolderWithPBProject((IFolder) this.parentResource);
//			if (pbFolder != null) {
//				apiPath = pbFolder.getLocation();
//			}
			break;
		default:
			throw new InvocationTargetException(new Exception("Wrong parent resource - check validation"));
		}
		
		final IJavaProject iJavaProject = JavaCore.create(this.parentResource.getProject());
		final IPackageFragmentRoot[] roots = iJavaProject.getPackageFragmentRoots();
		IPath componentJavaPath = null;
		
		// BitMask
		final int HAS_JAVA_FILES = 8;
		final int HAS_COMPLETE_PACKAGE = 4;
		final int HAS_PARTIAL_PACKAGE = 2;
		final int HAS_NO_TEST_SOURCE = 1;
		
		/*
		 * It is a little bit complicated to find the right package folder,
		 * as projects may have more than one source folder (e.g. source, test source, resource, test resource).
		 * We prioritize found PackageFragmentRoots in the order according
		 * to a bitmask with values above.
		 * For example a fragmentroot with java files, exisiting packages and no "test" name has a value of 15 (complete packages imply partial packages),
		 * while a test resource folder may have the value 0.
		 * 
		 * Paths are stored in possiblePaths under its priority number. Priorities are sorted in reverse order (highst first).
		 * Only the first path for a priority is stored.
		 */
		final TreeMap<Integer, IPath> possiblePaths = new TreeMap<>(Comparator.reverseOrder());
		
		final ILog logger = ILog.get();
		for (final IPackageFragmentRoot root: roots) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				int priority = 0;
				
				final boolean hasCompletePackageName = root.getPackageFragment(packageName).exists();
				if (hasCompletePackageName) {
					priority += HAS_COMPLETE_PACKAGE;
				}
				
				// FIXME: There is probably a correct way to get the test source state
				final boolean isTestSource = List.of(root.getCorrespondingResource().getProjectRelativePath().segments()).contains("test");
				if (!isTestSource) {
					priority += HAS_NO_TEST_SOURCE;
				}
				
				final boolean hasPackageNamePrefix = containsPackageName(root);
				if (hasPackageNamePrefix) {
					priority += HAS_PARTIAL_PACKAGE;
				}
				
				final boolean hasJavaFiles = containsJavaFiles(root);
				if (hasJavaFiles) {
					priority += HAS_JAVA_FILES;
				}
				
				final IPath javaPath = root.getCorrespondingResource().getLocation();
				
				logger.info("root is source with path "+javaPath + 
					" complete pacakges: "+hasCompletePackageName +
					" test source: " + isTestSource +
					" package prefix: "+ hasPackageNamePrefix +
					" java files: "+ hasJavaFiles +
					" - final priority: " + priority);
				
				possiblePaths.computeIfAbsent(priority, k -> javaPath);
			}
		}
		
		possiblePaths.forEach((k, v) -> logger.info("Paths: " + k +" - "+ v));
		// get the first path ordered by priority
		componentJavaPath = possiblePaths.firstEntry().getValue();
		
		if (packageName != null && !packageName.isEmpty()) {
			componentJavaPath = componentJavaPath.append(new Path(packageName.replace('.', '/')));
		}
		
		logger.info("Final Java Path: "+componentJavaPath);
		
		prepareFolder(componentFolder, monitor);
		String projectName = this.parentResource.getProject().getName();
		IPath path = componentFolder.getLocation();
		IPath projectRelativeJavaPath = componentJavaPath.removeFirstSegments(this.parentResource.getProject().getLocation().segmentCount());
		IFolder javaSourceFolder = this.parentResource.getProject().getFolder(projectRelativeJavaPath);
		prepareFolder(javaSourceFolder, monitor);
		ComponentEngine componentEngine = new ComponentEngine();
		try {
			componentEngine.init();
		} catch (Exception e) {
			WizardsPlugin.getDefault().log(e);
			throw new InvocationTargetException(e);
		}
		// TODO: select template in the user interface
//		componentEngine.setSelectedTemplateName(componentEngine.names()[0]);
		componentEngine.setProjectName(projectName);
		componentEngine.setCreateBodyTag(this.createBodyTag);
		componentEngine.setComponentName(this.componentName);
		componentEngine.setPackageName(this.packageName);
		componentEngine.setSuperclassName(this.superclassName);
		componentEngine.setComponentPath(path);
		componentEngine.setApiPath(apiPath);
		componentEngine.setJavaPath(componentJavaPath);
		componentEngine.setCreateApiFile(this.createApiFile);
		componentEngine.setHTMLBodyType(this.htmlBodyType);
		componentEngine.setWOOEncoding(this.wooEncoding);

		try {
			componentEngine.run(new NullProgressMonitor());
			this.parentResource.getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
			IResource[] resources = new IResource[] {componentFolderToReveal.findMember(this.componentName + ".java"), componentFolder.findMember(this.componentName + ".wod")};
			page.setResourcesToReveal(resources);
		} catch (Exception e) {
			WizardsPlugin.getDefault().log(e);
			throw new InvocationTargetException(e);
		}
	}

	/* Search for Java files in a package fragment root. Java files are strong
	 * indicators for Java source folders, in contrast to resource folders.
	 */
	private boolean containsJavaFiles(IPackageFragmentRoot root) {
		try {
			for (IJavaElement fragment: root.getChildren()) {
				if (fragment instanceof IPackageFragment pfragment) {
					if (pfragment.getCompilationUnits().length > 0) {
						return true;
					}
		    	}
			}
		} catch (JavaModelException e) {
			final ILog logger = ILog.get();
			logger.error("Could not get children of "+root, e);
		}
		return false;
	}

	/**
	 * Search for the {@link #packageName} and its parents.
	 * @param root
	 * @return do the packageName or one of its parent packages exist?
	 */
	private boolean containsPackageName(IPackageFragmentRoot root) {
		String searchPackage = packageName;
		while (searchPackage != null && !searchPackage.isEmpty()) {
			if (root.getPackageFragment(searchPackage).exists()) {
				return true;
			}
			int lastDot = searchPackage.lastIndexOf('.');
			if (lastDot >= 0) {
				searchPackage = searchPackage.substring(0, lastDot);
			} else {
				searchPackage = null;
			}
		}
		return false;
	}

	private void prepareFolder(IFolder _folder, IProgressMonitor _progressMonitor) throws CoreException {
		if (!_folder.exists()) {
			IContainer parent = _folder.getParent();
			if (parent instanceof IFolder fld) {
				prepareFolder(fld, _progressMonitor);
			}
			_folder.create(false, true, _progressMonitor);
		}
	}
}
