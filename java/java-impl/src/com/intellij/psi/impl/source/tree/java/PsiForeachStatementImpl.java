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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class PsiForeachStatementImpl extends CompositePsiElement implements PsiForeachStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiForeachStatementImpl");
  public PsiForeachStatementImpl() {
    super(FOREACH_STATEMENT);
  }

  @NotNull
  public PsiParameter getIterationParameter() {
    return (PsiParameter) findChildByRoleAsPsiElement(ChildRole.FOR_ITERATION_PARAMETER);
  }

  public PsiExpression getIteratedValue() {
    return (PsiExpression) findChildByRoleAsPsiElement(ChildRole.FOR_ITERATED_VALUE);
  }

  public PsiStatement getBody() {
    return (PsiStatement) findChildByRoleAsPsiElement(ChildRole.LOOP_BODY);
  }

  @NotNull
  public PsiJavaToken getLParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.LPARENTH);
  }

  public PsiJavaToken getRParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.RPARENTH);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch(role) {
      case ChildRole.LOOP_BODY:
        return findChildByType(STATEMENT_BIT_SET);

      case ChildRole.FOR_ITERATED_VALUE:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.FOR_KEYWORD:
        return getFirstChildNode();

      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.FOR_ITERATION_PARAMETER:
        return findChildByType(PARAMETER);

      case ChildRole.COLON:
        return findChildByType(COLON);

      default:
        return null;
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    IElementType i = child.getElementType();
    if (i == FOR_KEYWORD) {
      return ChildRole.FOR_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == PARAMETER) {
      return ChildRole.FOR_ITERATION_PARAMETER;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.FOR_ITERATED_VALUE;
      }
      else if (STATEMENT_BIT_SET.contains(child.getElementType())) {
        return ChildRole.LOOP_BODY;
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  public String toString() {
    return "PsiForeachStatement";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this)
      // Parent element should not see our vars
      return true;

    final PsiParameter iterationParameter = getIterationParameter();
    if (iterationParameter != null) {
      return processor.execute(iterationParameter, state);
    }

    return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitForeachStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
