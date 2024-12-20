/*
 * ====================================================================
 * 
 * The ObjectStyle Group Software License, Version 1.0
 * 
 * Copyright (c) 2006 The ObjectStyle Group and individual authors of the
 * software. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. The end-user documentation included with the redistribution, if any, must
 * include the following acknowlegement: "This product includes software
 * developed by the ObjectStyle Group (http://objectstyle.org/)." Alternately,
 * this acknowlegement may appear in the software itself, if and wherever such
 * third-party acknowlegements normally appear.
 * 
 * 4. The names "ObjectStyle Group" and "Cayenne" must not be used to endorse or
 * promote products derived from this software without prior written permission.
 * For written permission, please contact andrus@objectstyle.org.
 * 
 * 5. Products derived from this software may not be called "ObjectStyle" nor
 * may "ObjectStyle" appear in their names without prior written permission of
 * the ObjectStyle Group.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * OBJECTSTYLE GROUP OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * This software consists of voluntary contributions made by many individuals on
 * behalf of the ObjectStyle Group. For more information on the ObjectStyle
 * Group, please see <http://objectstyle.org/>.
 *  
 */
package org.objectstyle.wolips.eomodeler.actions;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.objectstyle.wolips.baseforuiplugins.utils.ErrorUtils;
import org.objectstyle.wolips.eomodeler.core.model.EODatabaseConfig;
import org.objectstyle.wolips.eomodeler.core.model.EOModel;
import org.objectstyle.wolips.eomodeler.core.model.EOModelVerificationFailure;
import org.objectstyle.wolips.eomodeler.core.model.IEOClassLoaderFactory;
import org.objectstyle.wolips.eomodeler.core.sql.IEOSQLReverseEngineer;
import org.objectstyle.wolips.eomodeler.core.sql.IEOSQLReverseEngineerFactory;
import org.objectstyle.wolips.eomodeler.core.utils.EOModelUtils;
import org.objectstyle.wolips.eomodeler.editors.EOModelErrorDialog;
import org.objectstyle.wolips.eomodeler.utils.StringLabelProvider;

public class ReverseEngineerAction extends EMAction {
	public void run(IAction action) {
		try {
			Object selectedObject = getSelectedObject();
			EOModel model = EOModelUtils.getRelatedModel(selectedObject);
			if (model == null) {
				MessageDialog.openError(getWindow().getShell(), "Select a Model", "Select a model to reverse engineer into.");
			} else {
				EODatabaseConfig activeDatabaseConfig = model.getActiveDatabaseConfig();
				ClassLoader eomodelClassLoader = IEOClassLoaderFactory.Utility.createClassLoader(model);
				IEOSQLReverseEngineer reverseEngineer = IEOSQLReverseEngineerFactory.Utility.reverseEngineerFactory().reverseEngineer(activeDatabaseConfig, eomodelClassLoader);
				List<String> tableNames = reverseEngineer.reverseEngineerTableNames();

				//ListSelectionDialog dlg = new ListSelectionDialog(getWindow().getShell(), tableNames, new StringContentProvider(), new StringLabelProvider(), "Select the tables to reverse engineer:");
				ListSelectionDialog dlg = ListSelectionDialog.of(tableNames)
															 .contentProvider(new StringContentProvider())
															 .labelProvider(new StringLabelProvider())
															 .message("Select the tables to reverse engineer:")
															 .create(getWindow().getShell());
				dlg.setInitialSelections(tableNames.toArray());
				dlg.setTitle("Reverse Engineer");
				if (dlg.open() == Window.OK) {
					Object[] selectedTableNameObjs = dlg.getResult();
					String[] selectedTableNames = new String[selectedTableNameObjs.length];
					System.arraycopy(selectedTableNameObjs, 0, selectedTableNames, 0, selectedTableNameObjs.length);
					List<String> selectedTableNamesList = Arrays.asList(selectedTableNames);
					File reverseEngineeredEOModelFolder = reverseEngineer.reverseEngineerWithTableNamesIntoModel(selectedTableNamesList);
					Set<EOModelVerificationFailure> failures = new HashSet<EOModelVerificationFailure>();
					model.importEntitiesFromModel(reverseEngineeredEOModelFolder.toURI().toURL(), failures);
					if (failures.size() > 0) {
						EOModelErrorDialog dialog = new EOModelErrorDialog(getWindow().getShell(), failures);
						dialog.open();
					}
					model.setReverseEngineered(true);
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			ErrorUtils.openErrorDialog(Display.getDefault().getActiveShell(), e);
		}
	}

	protected class StringContentProvider implements IStructuredContentProvider {
		public void dispose() {
			// DO NOTHING
		}

		public Object[] getElements(Object inputElement) {
			return ((List) inputElement).toArray();
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// DO NOTHING
		}
	}
}
