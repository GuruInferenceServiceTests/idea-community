/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;

import java.io.*;

public class ResizeableMappedFile implements Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ResizeableMappedFile");

  private long myLogicalSize;
  private final PagedFileStorage myStorage;

  public ResizeableMappedFile(final File file, int initialSize) throws IOException {
    myStorage = new PagedFileStorage(file);
    if (!file.exists() || file.length() == 0) {
      writeLength(0);
    }

    myLogicalSize = readLength();
    if (myLogicalSize == 0) {
      resize(initialSize);
    }
  }

  public long length() {
    return myLogicalSize;
  }

  private long realSize() {
    return myStorage.length();
  }

  private void resize(final int size) {
    try {
      myStorage.resize(size);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void ensureSize(final long pos) {
    myLogicalSize = Math.max(pos, myLogicalSize);
    while (pos >= realSize()) {
      expand();
    }
  }

  private void expand() {
    resize((int)((realSize() + 1) * 13) >> 3);
  }

  private File getLengthFile() {
    return new File(myStorage.getFile().getPath() + ".len");
  }

  private void writeLength(final long len) {
    File lengthFile = getLengthFile();
    DataOutputStream stream = null;
    try {
      stream = new DataOutputStream(new FileOutputStream(lengthFile));
      stream.writeLong(len);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  public boolean isDirty() {
    return myStorage.isDirty();
  }

  public void force() {
    if (isDirty()) {
      writeLength(myLogicalSize);
    }
    myStorage.force();
  }

  private long readLength() {
    File lengthFile = getLengthFile();
    DataInputStream stream = null;
    try {
      stream = new DataInputStream(new FileInputStream(lengthFile));
      return stream.readLong();
    }
    catch (IOException e) {
      writeLength(realSize());
      return realSize();
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  public int getInt(int index) {
    return myStorage.getInt(index);
  }

  public void putInt(int index, int value) {
    ensureSize(index + 4);
    myStorage.putInt(index, value);
  }

  public long getLong(int index) {
    return myStorage.getLong(index);
  }

  public void putLong(int index, long value) {
    ensureSize(index + 8);
    myStorage.putLong(index, value);
  }

  public byte get(int index) {
    return myStorage.get(index);
  }

  public void put(int index, byte value) {
    ensureSize(index + 1);
    myStorage.put(index, value);
  }

  public void get(int index, byte[] dst, int offset, int length) {
    myStorage.get(index, dst, offset, length);
  }

  public void put(int index, byte[] src, int offset, int length) {
    ensureSize(index + length);
    myStorage.put(index, src, offset, length);
  }

  public void close() {
    force();
    myStorage.close();
  }

}