/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.Nullable;

class ConcreteClassUtil {

    private ConcreteClassUtil() {
        super();
    }

    public static boolean typeIsConcreteClass(
            @Nullable PsiTypeElement typeElement) {
        if (typeElement == null) {
            return false;
        }
        final PsiType type = typeElement.getType();
        final PsiType baseType = type.getDeepComponentType();
        if (!(baseType instanceof PsiClassType)) {
            return false;
        }
        final PsiClass aClass = ((PsiClassType) baseType).resolve();
        if (aClass == null) {
            return false;
        }
        if (aClass.isInterface() || aClass.isEnum()||
                aClass.isAnnotationType()) {
            return false;
        }
        if(aClass instanceof PsiTypeParameter) {
            return false;
        }
        return !LibraryUtil.classIsInLibrary(aClass);
    }
}
