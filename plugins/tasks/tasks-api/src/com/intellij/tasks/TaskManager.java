/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.tasks;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManager {

  public static TaskManager getManager(@NotNull Project project) {
    return project.getComponent(TaskManager.class);
  }

  /**
   * Queries all configured task repositories.
   * Operation may be blocked for a while.
   * @param query text search
   * @return up-to-date issues retrieved from repositories
   * @see #getCachedIssues()
   */
  public abstract List<Task> getIssues(String query);

  /**
   * Returns already cached issues.
   * @return cached issues.
   */
  public abstract List<Task> getCachedIssues();

  @Nullable
  public abstract Task updateIssue(String id); 

  public abstract LocalTask[] getLocalTasks();

  public abstract LocalTask createLocalTask(String summary);

  public abstract void activateTask(@NotNull Task task, boolean clearContext, boolean createChangelist);

  @NotNull
  public abstract List<ChangeListInfo> getOpenChangelists(Task task);

  @NotNull
  public abstract LocalTask getActiveTask();

  /**
   * Update issue cache asynchronously
   * @param onComplete callback to be invoked after updating
   */
  public abstract void updateIssues(@Nullable Runnable onComplete);

  public abstract boolean isVcsEnabled();

  @Nullable
  public abstract LocalTask getAssociatedTask(LocalChangeList list);

  public abstract void associateWithTask(LocalChangeList changeList);

  public abstract void removeTask(LocalTask task);

  public abstract void addTaskListener(TaskListener listener);

  public abstract void removeTaskListener(TaskListener listener);
  // repositories management

  public abstract TaskRepository[] getAllRepositories();

  public abstract boolean testConnection(TaskRepository repository);

  public final static TaskRepositoryType[] ourRepositoryTypes = Extensions.getExtensions(TaskRepositoryType.EP_NAME);
}
