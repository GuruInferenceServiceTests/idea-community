/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.io.File;
import java.text.MessageFormat;

/**
 * author: lesya
 */
public class CheckoutHelper {
  private File myCheckoutLocation;
  private final CvsRootConfiguration myConfiguration;
  private final Component myPanel;
  private CvsElement myElement;

  public CheckoutHelper(CvsRootConfiguration configuration, Component panel) {
    myConfiguration = configuration;
    myPanel = panel;
  }

  public boolean prepareCheckoutData(CvsElement element, boolean useAltCheckoutPath, String altCheckoutPath) {
    myElement = element;
    if (!useAltCheckoutPath) {

      if (!requestLocation()) return false;

      if (shouldCreateDirectoryOfTheSameName()) {
        if (!creatingConfirmation()) return false;
      }

    }
    return true;

  }

  private boolean creatingConfirmation() {
    File checkoutDirectory = new File(myCheckoutLocation, myElement.getCheckoutPath());
    if (checkoutDirectory.isDirectory()) return true;
    String message = com.intellij.CvsBundle.message("confirmation.text.directory.will.be.created", checkoutDirectory);
    return Messages.showYesNoDialog(message, com.intellij.CvsBundle.message("operation.name.check.out.project"), Messages.getQuestionIcon()) == 0;
  }

  private boolean shouldCreateDirectoryOfTheSameName() {
    int compareResult =
        new File(myCheckoutLocation.getName()).compareTo(new File(myElement.getCheckoutDirectoryName()));
    return compareResult == 0;
  }

  public VirtualFile chooseCheckoutLocation(String pathToSuggestedFolder) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(com.intellij.CvsBundle.message("dialog.description.select.a.directory.to.check.out.the.files.to"));
    VirtualFile suggestedCheckoutFolder = LocalFileSystem.getInstance().findFileByPath(pathToSuggestedFolder.replace(File.separatorChar, '/'));
    VirtualFile[] files = FileChooser.chooseFiles(myPanel, descriptor, suggestedCheckoutFolder);
    if (files.length == 0) return null;
    return files[0];
  }

  private boolean requestLocation() {
    VirtualFile virtualFile = chooseCheckoutLocation(myConfiguration.PATH_TO_WORKING_FOLDER);
    if (virtualFile == null) return false;
    myCheckoutLocation = VfsUtil.virtualToIoFile(virtualFile);
    myConfiguration.PATH_TO_WORKING_FOLDER = myCheckoutLocation.getAbsolutePath();
    return true;
  }

  public File getCheckoutLocation() {
    return myCheckoutLocation;
  }


}
