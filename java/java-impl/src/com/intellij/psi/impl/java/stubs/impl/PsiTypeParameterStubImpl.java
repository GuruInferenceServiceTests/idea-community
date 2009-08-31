/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PsiTypeParameterStubImpl extends StubBase<PsiTypeParameter> implements PsiTypeParameterStub {
  private final StringRef myName;

  public PsiTypeParameterStubImpl(final StubElement parent, final StringRef name) {
    super(parent, JavaStubElementTypes.TYPE_PARAMETER);
    myName = name;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiTypeParameter[").append(myName).append(']');
    return builder.toString();
  }
  
  @NotNull
  public List<PsiAnnotationStub> getAnnotations() {
    List<StubElement> children = getChildrenStubs();

    return ContainerUtil.mapNotNull(children, new Function<StubElement, PsiAnnotationStub>() {
      public PsiAnnotationStub fun(StubElement stubElement) {
        return stubElement instanceof PsiAnnotationStub ? (PsiAnnotationStub)stubElement : null;
      }
    });
  }
}