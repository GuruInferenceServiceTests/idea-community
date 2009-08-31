package com.intellij.ide.util.importProject;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 13, 2007
 */
public class LibraryDescriptor {
  
  public static enum Level {
    GLOBAL, PROJECT, MODULE
  }
  
  private String myName;
  private final Collection<File> myJars;
  private Level myLevel;
  
  public LibraryDescriptor(String name, Collection<File> jars) {
    myName = name;
    myJars = jars;
  }

  public String getName() {
    return myName != null? myName : "";
  }

  public void setName(final String name) {
    myName = name;
  }

  public Level getLevel() {
    if (myLevel != null) {
      return myLevel;
    }
    return myJars.size() > 1? Level.PROJECT : Level.MODULE;
  }

  public void setLevel(final Level level) {
    myLevel = level;
  }

  public Collection<File> getJars() {
    return Collections.unmodifiableCollection(myJars);
  }
  
  public void addJars(Collection<File> jars) {
    myJars.addAll(jars);
  }
  
  public void removeJars(Collection<File> jars) {
    myJars.removeAll(jars);
  }

  public String toString() {
    return "Lib[" + myName + "]";
  }
}