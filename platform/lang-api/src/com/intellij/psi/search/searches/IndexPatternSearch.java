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

package com.intellij.psi.search.searches;

import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.QueryFactory;
import com.intellij.util.Query;
import com.intellij.openapi.util.TextRange;

/**
 * Allows to search for occurrences of specified regular expressions in the comments
 * of Java source files.
 *
 * @author yole
 * @since 5.1
 * @see IndexPatternProvider
 * @see com.intellij.psi.search.PsiSearchHelper#findFilesWithTodoItems()
 */
public abstract class IndexPatternSearch extends QueryFactory<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
  public static IndexPatternSearch INDEX_PATTERN_SEARCH_INSTANCE;

  public static class SearchParameters {
    private final PsiFile myFile;
    private IndexPattern myPattern;
    private IndexPatternProvider myPatternProvider;
    private TextRange myRange;

    public SearchParameters(final PsiFile file, final IndexPattern pattern) {
      myFile = file;
      myPattern = pattern;
    }

    public SearchParameters(final PsiFile file, final IndexPatternProvider patternProvider) {
      myFile = file;
      myPatternProvider = patternProvider;
    }

    public PsiFile getFile() {
      return myFile;
    }

    public IndexPattern getPattern() {
      return myPattern;
    }

    public IndexPatternProvider getPatternProvider() {
      return myPatternProvider;
    }

    public TextRange getRange() {
      return myRange;
    }

    public void setRange(final TextRange range) {
      myRange = range;
    }
  }

  protected IndexPatternSearch() {
  }

  /**
   * Returns a query which can be used to process occurrences of the specified pattern
   * in the specified file. The query is executed by parsing the contents of the file.
   *
   * @param file    the file in which occurrences should be searched.
   * @param pattern the pattern to search for.
   * @return the query instance.
   */
  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPattern pattern) {
    final SearchParameters parameters = new SearchParameters(file, pattern);
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of the specified pattern
   * in the specified text range. The query is executed by parsing the contents of the file.
   *
   * @param file        the file in which occurrences should be searched.
   * @param pattern     the pattern to search for.
   * @param startOffset the start offset of the range to search.
   * @param endOffset   the end offset of the range to search.
   * @return the query instance.
   */
  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPattern pattern,
                                                     int startOffset, int endOffset) {
    final SearchParameters parameters = new SearchParameters(file, pattern);
    parameters.setRange(new TextRange(startOffset, endOffset));
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of any pattern from the
   * specified provider in the specified file. The query is executed by parsing the
   * contents of the file.
   *
   * @param file            the file in which occurrences should be searched.
   * @param patternProvider the provider the patterns from which are searched.
   * @return the query instance.
   */
  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPatternProvider patternProvider) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider);
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of any pattern from the
   * specified provider in the specified text range. The query is executed by parsing the
   * contents of the file.
   *
   * @param file            the file in which occurrences should be searched.
   * @param patternProvider the provider the patterns from which are searched.
   * @param startOffset     the start offset of the range to search.
   * @param endOffset       the end offset of the range to search.
   * @return the query instance.
   */
  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPatternProvider patternProvider,
                                                     int startOffset, int endOffset) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider);
    parameters.setRange(new TextRange(startOffset, endOffset));
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }

  /**
   * Returns the number of occurrences of any pattern from the specified provider
   * in the specified file. The returned value is taken from the index, and the file
   * is not parsed.
   *
   * @param file            the file in which occurrences should be searched.
   * @param patternProvider the provider the patterns from which are searched.
   * @return the number of pattern occurrences.
   */
  public static int getOccurrencesCount(PsiFile file, IndexPatternProvider patternProvider) {
    return INDEX_PATTERN_SEARCH_INSTANCE.getOccurrencesCountImpl(file, patternProvider);
  }

  /**
   * Returns the number of occurrences of the specified pattern
   * in the specified file. The returned value is taken from the index, and the file
   * is not parsed.
   *
   * @param file            the file in which occurrences should be searched.
   * @param pattern     the pattern to search for.
   * @return the number of pattern occurrences.
   */
  public static int getOccurrencesCount(PsiFile file, IndexPattern pattern) {
    return INDEX_PATTERN_SEARCH_INSTANCE.getOccurrencesCountImpl(file, pattern);
  }

  protected abstract int getOccurrencesCountImpl(PsiFile file, IndexPatternProvider provider);
  protected abstract int getOccurrencesCountImpl(PsiFile file, IndexPattern pattern);
}
