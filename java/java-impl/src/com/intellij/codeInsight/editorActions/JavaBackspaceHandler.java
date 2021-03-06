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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;

public class JavaBackspaceHandler extends BackspaceHandlerDelegate {
  private boolean myToDeleteGt;

  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset() - 1;
    myToDeleteGt = c =='<' &&
                        file instanceof PsiJavaFile &&
                        PsiUtil.isLanguageLevel5OrHigher(file)
                        && JavaTypedHandler.isAfterClassLikeIdentifierOrDot(offset, editor);
  }

  public boolean charDeleted(final char c, final PsiFile file, final Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();
    if (editor.getDocument().getTextLength() <= offset) return false; //virtual space after end of file

    char c1 = chars.charAt(offset);
    if (c == '<' && myToDeleteGt) {
      if (c1 != '>') return true;
      handleLTDeletion(editor, offset);
      return true;
    }
    return false;
  }

  private static void handleLTDeletion(final Editor editor, final int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    while (iterator.getStart() > 0 && !JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.retreat();
    }

    if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.advance();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance++;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance--;
      }
      else if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.advance();
    }

    if (balance < 0) {
      editor.getDocument().deleteString(offset, offset + 1);
    }
  }
}
