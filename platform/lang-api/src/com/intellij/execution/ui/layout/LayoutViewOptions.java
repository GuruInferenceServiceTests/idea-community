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

package com.intellij.execution.ui.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.content.Content;

public interface LayoutViewOptions {

  String STARTUP = "startup";

  @NotNull
  LayoutViewOptions setTopToolbar(@NotNull ActionGroup actions, @NotNull String place);

  LayoutViewOptions setLeftToolbar(@NotNull ActionGroup leftToolbar, @NotNull String place);

  @NotNull
  LayoutViewOptions setMinimizeActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setMoveToGridActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setAttractionPolicy(@NotNull String contentId, LayoutAttractionPolicy policy);
  LayoutViewOptions setConditionAttractionPolicy(@NotNull String condition, LayoutAttractionPolicy policy);

  boolean isToFocus(Content content, final String condition);

  LayoutViewOptions setToFocus(@Nullable Content content, final String condition);

  AnAction getLayoutActions();
  AnAction[] getLayoutActionsList();

  LayoutViewOptions setAdditionalFocusActions(ActionGroup group);

}