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

/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class EditorColorsSchemeImpl extends AbstractColorsScheme implements ExternalizableScheme {
  private final ExternalInfo myExternalInfo = new ExternalInfo();

  public EditorColorsSchemeImpl(EditorColorsScheme parenScheme, DefaultColorSchemesManager defaultColorSchemesManager) {
    super(parenScheme, defaultColorSchemesManager);
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------
  public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
    if (!Comparing.equal(attributes, getAttributes(key))) {
      myAttributesMap.put(key, attributes);
    }
  }

  public void setColor(ColorKey key, Color color) {
    if (!Comparing.equal(color, getColor(key))) {
      myColorsMap.put(key, color);
    }
  }

  public TextAttributes getAttributes(TextAttributesKey key) {
    if (myAttributesMap.containsKey(key)) {
      return myAttributesMap.get(key);
    } else {
      return myParentScheme.getAttributes(key);
    }
  }

  public Color getColor(ColorKey key) {
    if (myColorsMap.containsKey(key)) {
      return myColorsMap.get(key);
    } else {
      return myParentScheme.getColor(key);
    }
  }

  public Object clone() {
    EditorColorsSchemeImpl newScheme = new EditorColorsSchemeImpl(myParentScheme, DefaultColorSchemesManager.getInstance());
    copyTo(newScheme);
    newScheme.setName(getName());
    return newScheme;
  }

  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }
}
