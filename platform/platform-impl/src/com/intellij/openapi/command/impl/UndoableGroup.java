package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author max
 */
class UndoableGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoableGroup");

  private String myCommandName;
  private boolean myComplex;
  private int myCommandCounter;
  private boolean myTransparentsOnly;
  private ArrayList<UndoableAction> myActions;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private Project myProject;
  private final UndoConfirmationPolicy myUndoConfirmationPolicy;
  private boolean isValid = true;

  public UndoableGroup(String commandName,
                       boolean isComplex,
                       Project project,
                       EditorAndState stateBefore,
                       EditorAndState stateAfter,
                       int commandCounter,
                       UndoConfirmationPolicy undoConfirmationPolicy,
                       boolean transparentsOnly) {
    myCommandName = commandName;
    myComplex = isComplex;
    myCommandCounter = commandCounter;
    myActions = new ArrayList<UndoableAction>();
    myProject = project;
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
    myUndoConfirmationPolicy = undoConfirmationPolicy;
    myTransparentsOnly = transparentsOnly;
  }

  public boolean isComplex() {
    return myComplex;
  }

  public UndoableAction[] getActions() {
    return myActions.toArray(new UndoableAction[myActions.size()]);
  }

  public void addTailActions(Collection<UndoableAction> actions) {
    myActions.addAll(actions);
  }

  private Iterator<UndoableAction> reverseIterator(final ListIterator<UndoableAction> iter) {
    return new Iterator<UndoableAction>() {
      public boolean hasNext() {
        return iter.hasPrevious();
      }

      public UndoableAction next() {
        return iter.previous();
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private void undoOrRedo(final boolean isUndo) {
    LocalHistoryAction action = LocalHistoryAction.NULL;

    if (myProject != null) {
      if (isComplex()) {
        final String actionName;
        if (isUndo) {
          actionName = CommonBundle.message("local.vcs.action.name.undo.command", myCommandName);
        }
        else {
          actionName = CommonBundle.message("local.vcs.action.name.redo.command", myCommandName);
        }
        action = LocalHistory.startAction(myProject, actionName);
      }
    }

    try {
      performWritableUndoOrRedoAction(isUndo);
    }
    finally {
      action.finish();
    }
  }

  private void performWritableUndoOrRedoAction(final boolean isUndo) {
    final Iterator<UndoableAction> actions = isUndo ? reverseIterator(myActions.listIterator(myActions.size())) : myActions.iterator();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        String title;
        String message;
        if (isUndo) {
          title = CommonBundle.message("cannot.undo.dialog.title");
          message = CommonBundle.message("cannot.undo.message");
        }
        else {
          title = CommonBundle.message("cannot.redo.dialog.title");
          message = CommonBundle.message("cannot.redo.message");
        }

        while (actions.hasNext()) {
          UndoableAction undoableAction = actions.next();
          try {
            if (isUndo) {
              undoableAction.undo();
            }
            else {
              undoableAction.redo();
            }
          }
          catch (UnexpectedUndoException e) {
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              if (e.getMessage() != null) {
                message += ".\n" + e.getMessage();
              }
              Messages.showMessageDialog(myProject, message, title, Messages.getErrorIcon());
            }
            else {
              LOG.error(e);
            }
          }
        }
      }
    });
  }

  public void undo() {
    undoOrRedo(true);
  }

  public void redo() {
    undoOrRedo(false);
  }

  public Collection<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<DocumentReference>();
    for (UndoableAction action : myActions) {
      result.addAll(Arrays.asList(action.getAffectedDocuments()));
    }
    return result;
  }

  public EditorAndState getStateBefore() {
    return myStateBefore;
  }

  public EditorAndState getStateAfter() {
    return myStateAfter;
  }

  public void setStateBefore(EditorAndState stateBefore) {
    myStateBefore = stateBefore;
  }

  public void setStateAfter(EditorAndState stateAfter) {
    myStateAfter = stateAfter;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public int getCommandCounter() {
    return myCommandCounter;
  }

  public boolean askConfirmation() {
    if (myUndoConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION) {
      return true;
    }
    else if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
      return false;
    }
    else {
      return isComplex() || affectsMultiplePhysicalDocs();
    }
  }

  private boolean affectsMultiplePhysicalDocs() {
    return CommandMerger.areMultiplePhisicalDocsAffected(getAffectedDocuments());
  }

  public boolean isTransparentsOnly() {
    return myTransparentsOnly;
  }

  public void invalidateIfComplex() {
    if (!myComplex) return;
    isValid = false;
  }

  public boolean isValid() {
    return isValid;
  }

  public String toString() {
    @NonNls StringBuilder result = new StringBuilder("UndoableGroup{ ");
    for (UndoableAction action : myActions) {
      result.append(action).append(" ");
    }
    result.append("}");
    return result.toString();
  }
}