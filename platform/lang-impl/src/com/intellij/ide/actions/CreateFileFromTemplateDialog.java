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

package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author peter
 */
public class CreateFileFromTemplateDialog extends DialogWrapper {
  private JTextField myNameField;
  private TemplateKindCombo myKindCombo;
  private JPanel myPanel;
  private JLabel myUpDownHint;
  private JLabel myKindLabel;

  private ElementCreator myCreator;

  protected CreateFileFromTemplateDialog(@NotNull Project project) {
    super(project, true);

    myKindLabel.setLabelFor(myKindCombo);
    myKindCombo.registerUpDownHint(myNameField);
    myUpDownHint.setIcon(Icons.UP_DOWN_ARROWS);
    init();
  }

  protected JTextField getNameField() {
    return myNameField;
  }

  protected TemplateKindCombo getKindCombo() {
    return myKindCombo;
  }

  private String getEnteredName() {
    return getNameField().getText();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    if (myCreator.tryCreate(getEnteredName()).length == 0) {
      return;
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getNameField();
  }

  public static Builder createDialog(@NotNull final Project project) {
    final CreateFileFromTemplateDialog dialog = new CreateFileFromTemplateDialog(project);
    return new BuilderImpl(dialog, project);
  }

  private static class BuilderImpl implements Builder {

    private final CreateFileFromTemplateDialog myDialog;
    private final Project myProject;

    public BuilderImpl(CreateFileFromTemplateDialog dialog, Project project) {
      myDialog = dialog;
      myProject = project;
    }

    @Override
    public Builder setTitle(String title) {
      myDialog.setTitle(title);
      return this;
    }

    public Builder addKind(@NotNull String name, @Nullable Icon icon, @NotNull String templateName) {
      myDialog.getKindCombo().addItem(name, icon, templateName);
      return this;
    }

    public <T extends PsiElement> T show(@NotNull String errorTitle, @Nullable String selectedTemplateName,
                                         @NotNull final FileCreator<T> creator) {
      final Ref<T> created = Ref.create(null);
      myDialog.getKindCombo().setSelectedName(selectedTemplateName);
      myDialog.myCreator = new ElementCreator(myProject, errorTitle) {
        @Override
        protected void checkBeforeCreate(String newName) throws IncorrectOperationException {
          creator.checkBeforeCreate(newName, myDialog.getKindCombo().getSelectedName());
        }

        @Override
        protected PsiElement[] create(String newName) throws Exception {
          final T element = creator.createFile(myDialog.getEnteredName(), myDialog.getKindCombo().getSelectedName());
          created.set(element);
          if (element != null) {
            return new PsiElement[]{element};
          }
          return PsiElement.EMPTY_ARRAY;
        }

        @Override
        protected String getActionName(String newName) {
          return creator.getActionName(newName, myDialog.getKindCombo().getSelectedName());
        }
      };
      myDialog.show();
      if (myDialog.getExitCode() == OK_EXIT_CODE) {
        return created.get();
      }
      return null;
    }

    @Nullable
    @Override
    public Map<String,String> getCustomProperties() {
      return null;
    }
  }

  public interface Builder {
    Builder setTitle(String title);
    Builder addKind(@NotNull String kind, @Nullable Icon icon, @NotNull String templateName);
    @Nullable
    <T extends PsiElement> T show(@NotNull String errorTitle, @Nullable String selectedItem, @NotNull FileCreator<T> creator);
    @Nullable
    Map<String,String> getCustomProperties();
  }

  public interface FileCreator<T> {
    void checkBeforeCreate(@NotNull String name, @NotNull String templateName) throws IncorrectOperationException;

    @Nullable
    T createFile(@NotNull String name, @NotNull String templateName);

    @NotNull
    String getActionName(@NotNull String name, @NotNull String templateName);
  }
}
