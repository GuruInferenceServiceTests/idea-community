/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;

/**
 * @author sergey.evdokimov
 */
public abstract class GroovyFrameworkConfigNotification {
  public static final ExtensionPointName<GroovyFrameworkConfigNotification> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.groovyFrameworkConfigNotification");

  public abstract boolean hasFrameworkStructure(@NotNull Module module);

  public abstract boolean hasFrameworkLibrary(@NotNull Module module);

  public abstract EditorNotificationPanel createConfigureNotificationPanel(@NotNull Module module);

  public FileType[] getFrameworkFileTypes() {
    return FileType.EMPTY_ARRAY;
  }
}
