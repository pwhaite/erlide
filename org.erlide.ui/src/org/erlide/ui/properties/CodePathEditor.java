/*******************************************************************************
 * Copyright (c) 2008 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution.
 * 
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.ui.properties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.jface.preference.ListEditor;
import org.eclipse.swt.widgets.Composite;

public class CodePathEditor extends ListEditor {

	public CodePathEditor(String name, String labelText, Composite parent) {
		init(name, labelText);
		createControl(parent);
	}

	@Override
	protected String createList(String[] items) {
		StringBuffer path = new StringBuffer("");//$NON-NLS-1$

		for (int i = 0; i < items.length; i++) {
			path.append(items[i]);
			path.append(File.pathSeparator);
		}
		return path.toString();
	}

	@Override
	protected String getNewInputObject() {
		return null;
	}

	@Override
	protected String[] parseString(String stringList) {
		StringTokenizer st = new StringTokenizer(stringList, File.pathSeparator
				+ "\n\r");//$NON-NLS-1$
		List<String> v = new ArrayList<String>();
		while (st.hasMoreElements()) {
			v.add((String) st.nextElement());
		}
		return v.toArray(new String[v.size()]);
	}

}