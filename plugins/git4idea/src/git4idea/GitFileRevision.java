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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * Git file revision
 */
public class GitFileRevision extends VcsFileRevisionEx implements Comparable<VcsFileRevision> {
  /**
   * encoding to be used for binary output
   */
  @SuppressWarnings({"HardCodedStringLiteral"}) private final static Charset BIN_ENCODING = Charset.forName("ISO-8859-1");
  private final FilePath path;
  private final GitRevisionNumber revision;
  private final Pair<Pair<String, String>, Pair<String, String>> authorAndCommitter;
  private final String message;
  private byte[] content;
  private final Project project;
  private final String branch;

  public GitFileRevision(@NotNull Project project, @NotNull FilePath path, @NotNull GitRevisionNumber revision) {
    this(project, path, revision, null, null, null);
  }

  public GitFileRevision(@NotNull Project project,
                         @NotNull FilePath path,
                         @NotNull GitRevisionNumber revision,
                         @Nullable Pair<Pair<String, String>, Pair<String, String>> authorAndCommitter,
                         @Nullable String message,
                         @Nullable String branch) {
    this.project = project;
    this.path = path;
    this.revision = revision;
    this.authorAndCommitter = authorAndCommitter;
    this.message = message;
    this.branch = branch;
  }

  /**
   * @return file path
   */
  public FilePath getPath() {
    return path;
  }

  public VcsRevisionNumber getRevisionNumber() {
    return revision;
  }

  public Date getRevisionDate() {
    return revision.getTimestamp();
  }

  public String getAuthor() {
    return authorAndCommitter.getFirst().getFirst();
  }

  @Override
  public String getAuthorEmail() {
    return authorAndCommitter.getFirst().getSecond();
  }

  @Override
  public String getCommitterName() {
    return authorAndCommitter.getSecond() == null ? null : authorAndCommitter.getSecond().getFirst();
  }

  @Override
  public String getCommitterEmail() {
    return authorAndCommitter.getSecond() == null ? null : authorAndCommitter.getSecond().getSecond();
  }

  public String getCommitMessage() {
    return message;
  }

  public String getBranchName() {
    return branch;
  }

  public synchronized void loadContent() throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(path);
    if (content == null) {
      content = GitFileUtils.getFileContent(project, root, revision.getRev(), GitUtil.relativePath(root, path));
      if (content == null) {
        content = new byte[0];
      }
    }
  }

  public synchronized byte[] getContent() throws IOException {
    if (content == null) {
      try {
        loadContent();
      }
      catch (VcsException e) {
        throw new IOException(e.getMessage());
      }
    }
    return content;
  }

  public int compareTo(VcsFileRevision rev) {
    if (rev instanceof GitFileRevision) return revision.compareTo(((GitFileRevision)rev).revision);
    return getRevisionDate().compareTo(rev.getRevisionDate());
  }
}
