package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author cdr
 */
public class SliceUtil {
  public static boolean processUsagesFlownDownTo(@NotNull PsiElement expression, @NotNull Processor<SliceUsage> processor, @NotNull SliceUsage parent,
                                                 @NotNull PsiSubstitutor parentSubstitutor) {
    expression = simplify(expression);
    PsiElement original = expression;
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)expression;
      JavaResolveResult result = ref.advancedResolve(false);
      parentSubstitutor = result.getSubstitutor().putAll(parentSubstitutor);
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiMethod && expression.getParent() instanceof PsiMethodCallExpression) {
        return processUsagesFlownDownTo(expression.getParent(), processor, parent, parentSubstitutor);
      }
      if (!(resolved instanceof PsiVariable)) return true;
      expression = resolved;
    }
    if (expression instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)expression;

      final Set<PsiExpression> expressions = new THashSet<PsiExpression>(DfaUtil.getCachedVariableValues(variable, original));
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null && expressions.isEmpty()) expressions.add(initializer);
      for (PsiExpression exp : expressions) {
        if (!handToProcessor(exp, processor, parent, parentSubstitutor)) return false;
      }
      if (variable instanceof PsiField) {
        return processFieldUsages((PsiField)variable, processor, parent, parentSubstitutor);
      }
      else if (variable instanceof PsiParameter) {
        return processParameterUsages((PsiParameter)variable, processor, parent, parentSubstitutor);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      return processMethodReturnValue((PsiMethodCallExpression)expression, processor, parent, parentSubstitutor);
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)expression;
      PsiExpression thenE = conditional.getThenExpression();
      PsiExpression elseE = conditional.getElseExpression();
      if (thenE != null && !handToProcessor(thenE, processor, parent, parentSubstitutor)) return false;
      if (elseE != null && !handToProcessor(elseE, processor, parent, parentSubstitutor)) return false;
    }
    return true;
  }

  private static PsiElement simplify(@NotNull PsiElement expression) {
    if (expression instanceof PsiParenthesizedExpression) {
      return simplify(((PsiParenthesizedExpression)expression).getExpression());
    }
    if (expression instanceof PsiTypeCastExpression) {
      return simplify(((PsiTypeCastExpression)expression).getOperand());
    }
    return expression;
  }

  private static boolean handToProcessor(@NotNull PsiExpression exp,
                                         @NotNull Processor<SliceUsage> processor,
                                         @NotNull SliceUsage parent,
                                         @NotNull PsiSubstitutor substitutor) {
    final PsiExpression realExpression =
      exp.getParent() instanceof DummyHolder ? (PsiExpression)((DummyHolder)exp.getParent()).getContext() : exp;
    assert realExpression != null;
    if (!(realExpression instanceof PsiCompiledElement)) {
      SliceUsage usage = createSliceUsage(realExpression, parent, substitutor);
      if (!processor.process(usage)) return false;
    }
    return true;
  }

  private static boolean processMethodReturnValue(@NotNull final PsiMethodCallExpression methodCallExpr,
                                                  @NotNull final Processor<SliceUsage> processor,
                                                  @NotNull final SliceUsage parent,
                                                  @NotNull final PsiSubstitutor parentSubstitutor) {
    final JavaResolveResult resolved = methodCallExpr.resolveMethodGenerics();
    final PsiElement r = resolved.getElement();
    if (!(r instanceof PsiMethod)) return true;
    PsiMethod methodCalled = (PsiMethod)r;

    PsiType returnType = methodCalled.getReturnType();
    if (returnType == null) return true;
    final PsiCodeBlock body = methodCalled.getBody();
    if (body == null) return true;

    final boolean[] result = {true};
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // do not look for returns there
      }

      public void visitReturnStatement(final PsiReturnStatement statement) {
        PsiExpression returnValue = statement.getReturnValue();
        if (returnValue == null) return;
        PsiSubstitutor substitutor = resolved.getSubstitutor().putAll(parentSubstitutor);

        if (!handToProcessor(returnValue, processor, parent, substitutor)) {
          stopWalking();
          result[0] = false;
        }
      }
    });

    return result[0];
  }

  private static boolean processFieldUsages(@NotNull final PsiField field, @NotNull final Processor<SliceUsage> processor, @NotNull final SliceUsage parent,
                                            @NotNull final PsiSubstitutor parentSubstitutor) {
    if (field.hasInitializer()) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null && !(field instanceof PsiCompiledElement)) {
        if (!handToProcessor(initializer, processor, parent, parentSubstitutor)) return false;
      }
    }
    return ReferencesSearch.search(field, parent.getScope().toSearchScope()).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        SliceManager.getInstance(field.getProject()).checkCanceled();
        PsiElement element = reference.getElement();
        if (!(element instanceof PsiReferenceExpression)) return true;
        if (element instanceof PsiCompiledElement) return true;
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        PsiElement parentExpr = referenceExpression.getParent();
        if (PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
          PsiExpression rExpression = ((PsiAssignmentExpression)parentExpr).getRExpression();
          PsiType rtype = rExpression.getType();
          PsiType ftype = field.getType();
          if (TypeConversionUtil.isAssignable(parentSubstitutor.substitute(ftype), parentSubstitutor.substitute(rtype))) {
            return handToProcessor(rExpression, processor, parent, parentSubstitutor);
          }
        }
        if (parentExpr instanceof PsiPrefixExpression && ((PsiPrefixExpression)parentExpr).getOperand() == referenceExpression && ( ((PsiPrefixExpression)parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPrefixExpression)parentExpr).getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
          PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parentExpr;
          return handToProcessor(prefixExpression, processor, parent, parentSubstitutor);
        }
        if (parentExpr instanceof PsiPostfixExpression && ((PsiPostfixExpression)parentExpr).getOperand() == referenceExpression && ( ((PsiPostfixExpression)parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPostfixExpression)parentExpr).getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
          PsiPostfixExpression postfixExpression = (PsiPostfixExpression)parentExpr;
          return handToProcessor(postfixExpression, processor, parent, parentSubstitutor);
        }
        return true;
      }
    });
  }

  public static SliceUsage createSliceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent, @NotNull PsiSubstitutor substitutor) {
    return new SliceUsage(simplify(element), parent, substitutor);
  }

  static boolean processParameterUsages(@NotNull final PsiParameter parameter, @NotNull final Processor<SliceUsage> processor, @NotNull final SliceUsage parent,
                                        @NotNull final PsiSubstitutor parentSubstitutor) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiMethod)) return true;
    final PsiMethod method = (PsiMethod)declarationScope;

    final int paramSeqNo = ArrayUtil.find(method.getParameterList().getParameters(), parameter);
    assert paramSeqNo != -1;

    Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
    superMethods.add(method);
    final Set<PsiReference> processed = new THashSet<PsiReference>(); //usages of super method and overridden method can overlap
    for (final PsiMethod containingMethod : superMethods) {
      if (!MethodReferencesSearch.search(containingMethod, parent.getScope().toSearchScope(), true).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          SliceManager.getInstance(parameter.getProject()).checkCanceled();
          synchronized (processed) {
            if (!processed.add(reference)) return true;
          }
          PsiElement refElement = reference.getElement();
          PsiExpressionList argumentList;
          JavaResolveResult result;
          if (refElement instanceof PsiCall) {
            // the case of enum constant decl
            PsiCall call = (PsiCall)refElement;
            argumentList = call.getArgumentList();
            result = call.resolveMethodGenerics();
          }
          else {
            PsiElement element = refElement.getParent();
            if (element instanceof PsiCompiledElement) return true;
            if (element instanceof PsiAnonymousClass) {
              PsiAnonymousClass anon = (PsiAnonymousClass)element;
              argumentList = anon.getArgumentList();
              PsiElement callExp = element.getParent();
              if (!(callExp instanceof PsiCallExpression)) return true;
              result = ((PsiCall)callExp).resolveMethodGenerics();
            }
            else {
              if (!(element instanceof PsiCall)) return true;
              PsiCall call = (PsiCall)element;
              argumentList = call.getArgumentList();
              result = call.resolveMethodGenerics();
            }
          }
          PsiSubstitutor substitutor = result.getSubstitutor();

          PsiExpression[] expressions = argumentList.getExpressions();
          if (paramSeqNo < expressions.length) {
            PsiExpression passExpression = expressions[paramSeqNo];

            Project project = argumentList.getProject();
            PsiElement element = result.getElement();
            // for erased method calls for which we cannot determine target substitutor,
            // rely on call argument types. I.e. new Pair(1,2) -> Pair<Integer, Integer>
            if (element instanceof PsiTypeParameterListOwner && PsiUtil.isRawSubstitutor((PsiTypeParameterListOwner)element, substitutor)) {
              PsiTypeParameter[] typeParameters = substitutor.getSubstitutionMap().keySet().toArray(new PsiTypeParameter[0]);
              PsiParameter[] parameters = method.getParameterList().getParameters();

              PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
              substitutor = resolveHelper.inferTypeArguments(typeParameters, parameters, expressions, parentSubstitutor, argumentList, false);
            }

            substitutor = removeRawMappingsLeftFromResolve(substitutor);

            PsiSubstitutor combined = unify(substitutor, parentSubstitutor, project);
            if (combined != null) {
              return handToProcessor(passExpression, processor, parent, combined);
            }
          }
          return true;
        }
      })) {
        return false;
      }
    }

    return true;
  }

  private static PsiSubstitutor removeRawMappingsLeftFromResolve(PsiSubstitutor substitutor) {
    Map<PsiTypeParameter, PsiType> map = null;
    for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      if (entry.getValue() == null) {
        if (map == null) map = new THashMap<PsiTypeParameter, PsiType>();
        map.put(entry.getKey(), entry.getValue());
      }
    }
    if (map != null) {
      Map<PsiTypeParameter, PsiType> newmap = new THashMap<PsiTypeParameter, PsiType>(substitutor.getSubstitutionMap());
      newmap.keySet().removeAll(map.keySet());
      substitutor = PsiSubstitutorImpl.createSubstitutor(newmap);
    }
    return substitutor;
  }

  private static PsiSubstitutor unify(PsiSubstitutor substitutor, PsiSubstitutor parentSubstitutor, final Project project) {
    Map<PsiTypeParameter,PsiType> newMap = new THashMap<PsiTypeParameter, PsiType>(substitutor.getSubstitutionMap());

    for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      PsiTypeParameter typeParameter = entry.getKey();
      PsiType type = entry.getValue();
      PsiClass resolved = PsiUtil.resolveClassInType(type);
      if (!parentSubstitutor.getSubstitutionMap().containsKey(typeParameter)) continue;
      PsiType parentType = parentSubstitutor.substitute(parentSubstitutor.substitute(typeParameter));

      if (resolved instanceof PsiTypeParameter) {
        PsiTypeParameter res = (PsiTypeParameter)resolved;
        newMap.put(res, parentType);
      }
      else if (!Comparing.equal(type, parentType)) {
        return null; // cannot unify
      }
    }
    return JavaPsiFacade.getElementFactory(project).createSubstitutor(newMap);
  }
}