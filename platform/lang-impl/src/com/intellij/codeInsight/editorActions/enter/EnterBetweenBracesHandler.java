package com.intellij.codeInsight.editorActions.enter;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.CodeInsightSettings;

public class EnterBetweenBracesHandler implements EnterHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesHandler");

  public Result preprocessEnter(final PsiFile file, final Editor editor, final Ref<Integer> caretOffsetRef, final Ref<Integer> caretAdvance,
                                final DataContext dataContext, final EditorActionHandler originalHandler) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int caretOffset = caretOffsetRef.get().intValue();
    if (CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
      // special case: enter inside "()" or "{}"
      if (caretOffset > 0 && caretOffset < text.length() && ((text.charAt(caretOffset - 1) == '(' && text.charAt(caretOffset) == ')') ||
                                                             (text.charAt(caretOffset - 1) == '{' && text.charAt(caretOffset) == '}'))) {
        originalHandler.execute(editor, dataContext);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        try {
          CodeStyleManager.getInstance(file.getProject()).adjustLineIndent(file, editor.getCaretModel().getOffset());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return Result.Default;
      }
    }
    return Result.Continue;
  }
}