/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/

 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.

 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.connection;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;
import org.netbeans.lib.cvsclient.io.IStreamLogger;
import org.netbeans.lib.cvsclient.io.StreamUtilities;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.*;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.text.MessageFormat;

/**
 * Implements a connection to a pserver. See the cvs documents for more
 * information about different connection methods. PServer is popular where
 * security is not an issue. For secure connections, consider using a
 * kserver (Kerberos) or the GSSAPI.
 *
 * @author Robert Greig
 */
public final class PServerConnection
  implements IConnection {

  // Constants ==============================================================

  private static final int DEFAULT_PORT = 2401;

  // Fields =================================================================

  private final String userName;
  private final String encodedPassword;
  private final String repository;
  private final ConnectionSettings connectionSettings;

  private Socket socket;
  private InputStream socketInputStream;
  private OutputStream socketOutputStream;
  @NonNls private static final String ENCODED_PASSWORD_OUTPUT_MESSAGE = "@encodedPassword@";
  @NonNls private static final String SUCCESS_MESSAGE = "I LOVE YOU";
  @NonNls private static final String FAILED_MESSAGE = "I HATE YOU";
  // Setup ==================================================================

  public PServerConnection(ConnectionSettings connectionSettings,
                           String userName,
                           String encodedPassword,
                           String repository) {
    BugLog.getInstance().assertNotNull(userName);
    BugLog.getInstance().assertNotNull(repository);
    BugLog.getInstance().assertTrue(connectionSettings.getConnectionTimeout() >= 0, "Timeout must be >= 0");

    this.userName = userName;
    this.encodedPassword = (encodedPassword != null) ? encodedPassword : "";
    this.repository = repository;
    this.connectionSettings = connectionSettings;
  }

  // Implemented ============================================================)

  public InputStream getInputStream() {
    return socketInputStream;
  }

  public OutputStream getOutputStream() {
    return socketOutputStream;
  }

  public String getRepository() {
    return repository;
  }

  public void verify(IStreamLogger streamLogger) throws AuthenticationException {
    try {
      open(streamLogger);
    }
    finally {
      close();
    }
  }

  public void open(IStreamLogger streamLogger) throws AuthenticationException {
    openConnection("BEGIN AUTH REQUEST", "END AUTH REQUEST", streamLogger);
  }

  public void close() {
    if (socketInputStream != null) {
      try {
        socketInputStream.close();
      }
      catch (IOException ex) {
        BugLog.getInstance().showException(ex);
      }
      finally {
        socketInputStream = null;
      }
    }

    if (socketOutputStream != null) {
      try {
        socketOutputStream.close();
      }
      catch (IOException ex) {
        BugLog.getInstance().showException(ex);
      }
      finally {
        socketOutputStream = null;
      }
    }

    if (socket != null) {
      try {
        socket.close();
      }
      catch (IOException ex) {
        // ignore
      }
      finally {
        socket = null;
      }
    }
  }

  // Utils ==================================================================

  /**
   * Authenticate a connection with the server, using the specified
   * postamble and preamble.
   *
   * @param preamble  the preamble to use
   * @param postamble the postamble to use
   * @throws AuthenticationException if an error occurred
   *                                 Return the socket used to make the connection. The socket is
   *                                 guaranteed to be open if an exception has not been thrown
   */
  private void openConnection(@NonNls String preamble, @NonNls String postamble, IStreamLogger streamLogger) throws AuthenticationException {
    boolean error = true;

    try {
      createSocket();

      this.socketOutputStream = new BufferedOutputStream(socket.getOutputStream());
      this.socketInputStream = new BufferedInputStream(socket.getInputStream());

      final OutputStream loggingOutputStream = streamLogger.createLoggingOutputStream(this.socketOutputStream);
      final InputStream loggingInputStream = streamLogger.createLoggingInputStream(this.socketInputStream);

      writeLn(loggingOutputStream, preamble, "US-ASCII");
      writeLn(loggingOutputStream, repository);
      writeLn(loggingOutputStream, userName);

      writeLn(socketOutputStream, encodedPassword, "US-ASCII");
      writeLn(streamLogger.getOutputLogStream(), ENCODED_PASSWORD_OUTPUT_MESSAGE);

      writeLn(loggingOutputStream, postamble, "US-ASCII");
      loggingOutputStream.flush();

      String response = new StreamUtilities(null).readLine(loggingInputStream);
      if (response.equals(SUCCESS_MESSAGE)) {
        error = false;
        return;
      }

      if (response.length() == 0) {
        throw new AuthenticationException(JavaCvsSrcBundle.message("no.response.from.server.error.message"));
      }

      if (response.equals(FAILED_MESSAGE)) {
        throw new UnknownUserException(JavaCvsSrcBundle.message("wrong.password.or.unknown.user.error.message"));
      }

      response = removePrefix(response, "error ");
      response = removePrefix(response, "E ");
      throw new UnknownUserException(getMessage(JavaCvsSrcBundle.message("authentication.failed.error.message"), response));
    }
    catch (ConnectException ex) {
      throw new AuthenticationException(getMessage(JavaCvsSrcBundle.message("cannot.connect.to.host.error.message"), connectionSettings.getHostName()), ex);
    }
    catch (NoRouteToHostException ex) {
      throw new AuthenticationException(getMessage(JavaCvsSrcBundle.message("no.route.to.host.error.message"), connectionSettings.getHostName()), ex);
    }
    catch (IOException ex) {
      throw new AuthenticationException(getMessage(JavaCvsSrcBundle.message("i.o.error.while.connecting.to.host.error.mesage"), connectionSettings.getHostName()), ex);
    }
    finally {
      if (error) {
        close();
      }
    }
  }

  private static void writeLn(final OutputStream outputStream, final String line, @NonNls final String encoding) throws IOException {
    final String line1 = line + "\n";
    outputStream.write(line1.getBytes(encoding));
  }

  private static void writeLn(final OutputStream outputStream, final String line) throws IOException {
    final String line1 = line + "\n";
    outputStream.write(line1.getBytes());
  }

  private void createSocket() throws IOException {
    if (connectionSettings.isUseProxy()) {
      this.socket = connectionSettings.createProxyTransport();
    }
    else {
      this.socket = new Socket(connectionSettings.getHostName(), connectionSettings.getPort());
      this.socket.setSoTimeout(connectionSettings.getConnectionTimeout());
    }
  }

  // Utils ==================================================================

  private static String removePrefix(String response, @NonNls String prefix) {
    if (response.length() > prefix.length()
        && response.startsWith(prefix)) {
      return response.substring(prefix.length());
    }
    return response;
  }

  private static String getMessage(String messagePattern, String value) {
    return MessageFormat.format(messagePattern, new Object[]{value});
  }
}
