
package com.intellij.psi.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiToDocumentSynchronizer extends PsiTreeChangeAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiToDocumentSynchronizer");

  private final PsiDocumentManagerImpl myPsiDocumentManager;
  private final MessageBus myBus;
  private final Map<Document, Pair<DocumentChangeTransaction, Integer>> myTransactionsMap = new HashMap<Document, Pair<DocumentChangeTransaction, Integer>>();
  private final Object mySyncLock = this;

  private Document mySyncDocument = null;

  public PsiToDocumentSynchronizer(PsiDocumentManagerImpl psiDocumentManager, MessageBus bus) {
    myPsiDocumentManager = psiDocumentManager;
    myBus = bus;
  }

  @Nullable
  public DocumentChangeTransaction getTransaction(final Document document) {
    final Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(document);
    return pair != null ? pair.getFirst() : null;
  }

  public boolean isInSynchronization(final Document document) {
    synchronized (mySyncLock) {
      return mySyncDocument == document;
    }
  }

  private interface DocSyncAction {
    void syncDocument(Document document, PsiTreeChangeEventImpl event);
  }

  private void doSync(PsiTreeChangeEvent event, DocSyncAction syncAction) {
    if (!toProcessPsiEvent()) return;
    PsiFile psiFile = event.getFile();
    if (psiFile == null || psiFile.getNode() == null) return;

    DocumentEx document = getCachedDocument(psiFile);
    if (document == null || document instanceof DocumentWindow) return;

    TextBlock textBlock = getTextBlock(document, psiFile);

    if (!textBlock.isEmpty()) {
      LOG.error("Attempt to modify PSI for non-committed Document!");
      textBlock.clear();
    }

    textBlock.lock();
    try {
      syncAction.syncDocument(document, (PsiTreeChangeEventImpl)event);
    }
    finally {
      textBlock.unlock();
    }

    myPsiDocumentManager.commitOtherFilesAssociatedWithDocument(document, psiFile);

    final boolean insideTransaction = myTransactionsMap.containsKey(document);
    if(!insideTransaction){
      document.setModificationStamp(psiFile.getModificationStamp());
      SmartPointerManagerImpl.synchronizePointers(psiFile);
      if (LOG.isDebugEnabled()) {
        PsiDocumentManagerImpl.checkConsistency(psiFile, document);
      }
    }
  }

  public void childAdded(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        insertString(document, event.getOffset(), event.getChild().getText());
      }
    });
  }

  public void childRemoved(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        deleteString(document, event.getOffset(), event.getOffset() + event.getOldLength());
      }
    });
  }

  public void childReplaced(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        replaceString(document, event.getOffset(), event.getOffset() + event.getOldLength(), event.getNewChild().getText());
      }
    });
  }

  public void childrenChanged(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        replaceString(document, event.getOffset(), event.getOffset() + event.getOldLength(), event.getParent().getText());
      }
    });
  }

  private static boolean toProcessPsiEvent() {
    Application application = ApplicationManager.getApplication();
    return application.getCurrentWriteAction(CommitToPsiFileAction.class) == null
           && application.getCurrentWriteAction(ExternalChangeAction.class) == null;
  }


  public void replaceString(Document document, int startOffset, int endOffset, String s) {
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null) {
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, s);
    }
  }

  public void insertString(Document document, int offset, String s) {
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(offset, 0, s);
    }
  }

  private void deleteString(Document document, int startOffset, int endOffset){
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, "");
    }
  }

  @Nullable
  private DocumentEx getCachedDocument(PsiFile file) {
    return (DocumentEx)myPsiDocumentManager.getCachedDocument(file);
  }

  private TextBlock getTextBlock(Document document, PsiFile file) {
    return myPsiDocumentManager.getTextBlock(document, file);
  }

  public void startTransaction(Document doc, PsiElement scope) {
    Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(doc);
    if (pair == null) {
      final PsiFile psiFile = scope != null ? scope.getContainingFile() : null;
      pair = new Pair<DocumentChangeTransaction, Integer>(new DocumentChangeTransaction(doc, scope != null ? psiFile : null), 0);
      myBus.syncPublisher(PsiDocumentTransactionListener.TOPIC).transactionStarted(doc, psiFile);
    }
    else {
      pair = new Pair<DocumentChangeTransaction, Integer>(pair.getFirst(), pair.getSecond().intValue() + 1);
    }
    myTransactionsMap.put(doc, pair);
  }

  public void commitTransaction(Document document){
    final DocumentChangeTransaction documentChangeTransaction = removeTransaction(document);
    if(documentChangeTransaction == null) return;
    synchronized(mySyncLock){
      try {
        mySyncDocument = document;
        //if(documentChangeTransaction.getAffectedFragments().size() == 0) return; // Nothing to do

        final PsiElement changeScope = documentChangeTransaction.getChangeScope();
        final PsiTreeChangeEventImpl fakeEvent = new PsiTreeChangeEventImpl(changeScope.getManager());
        fakeEvent.setParent(changeScope);
        fakeEvent.setFile(changeScope.getContainingFile());
        doSync(fakeEvent, new DocSyncAction() {
          public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
            doCommitTransaction(document, documentChangeTransaction);
          }
        });

        myBus.syncPublisher(PsiDocumentTransactionListener.TOPIC).transactionCompleted(document, (PsiFile)changeScope);
      }
      finally {
        mySyncDocument = null;
      }
    }
  }

  public void doCommitTransaction(final Document document){
    doCommitTransaction(document, getTransaction(document));
  }

  private static void doCommitTransaction(final Document document, final DocumentChangeTransaction documentChangeTransaction) {
    DocumentEx ex = (DocumentEx) document;
    ex.suppressGuardedExceptions();
    try {
      boolean isReadOnly = !document.isWritable();
      ex.setReadOnly(false);
      final Set<Pair<MutableTextRange, StringBuffer>> affectedFragments = documentChangeTransaction.getAffectedFragments();
      for (final Pair<MutableTextRange, StringBuffer> pair : affectedFragments) {
        final StringBuffer replaceBuffer = pair.getSecond();
        final MutableTextRange range = pair.getFirst();
        if (replaceBuffer.length() == 0) {
          ex.deleteString(range.getStartOffset(), range.getEndOffset());
        }
        else if (range.getLength() == 0) {
          ex.insertString(range.getStartOffset(), replaceBuffer);
        }
        else {
          ex.replaceString(range.getStartOffset(),
                           range.getEndOffset(),
                           replaceBuffer);
        }
      }

      ex.setReadOnly(isReadOnly);
      //if(documentChangeTransaction.getChangeScope() != null) {
      //  LOG.assertTrue(document.getText().equals(documentChangeTransaction.getChangeScope().getText()),
      //                 "Psi to document synchronization failed (send to IK)");
      //}
    }
    finally {
      ex.unSuppressGuardedExceptions();
    }
  }

  @Nullable
  private DocumentChangeTransaction removeTransaction(Document doc) {
    Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(doc);
    if(pair == null) return null;
    if(pair.getSecond().intValue() > 0){
      pair = new Pair<DocumentChangeTransaction, Integer>(pair.getFirst(), pair.getSecond().intValue() - 1);
      myTransactionsMap.put(doc, pair);
      return null;
    }
    myTransactionsMap.remove(doc);
    return pair.getFirst();
  }

  public boolean isDocumentAffectedByTransactions(Document document) {
    return myTransactionsMap.containsKey(document);
  }


  public static class DocumentChangeTransaction{
    private final Set<Pair<MutableTextRange,StringBuffer>> myAffectedFragments = new TreeSet<Pair<MutableTextRange, StringBuffer>>(new Comparator<Pair<MutableTextRange, StringBuffer>>() {
      public int compare(final Pair<MutableTextRange, StringBuffer> o1,
                         final Pair<MutableTextRange, StringBuffer> o2) {
        return o1.getFirst().getStartOffset() - o2.getFirst().getStartOffset();
      }
    });
    private final Document myDocument;
    private final PsiFile myChangeScope;

    public DocumentChangeTransaction(final Document doc, PsiFile scope) {
      myDocument = doc;
      myChangeScope = scope;
    }

    public Set<Pair<MutableTextRange, StringBuffer>> getAffectedFragments() {
      return myAffectedFragments;
    }

    public PsiFile getChangeScope() {
      return myChangeScope;
    }

    public void replace(int start, int length, String str) {
      // calculating fragment
      // minimize replace
      final int oldStart = start;
      int end = start + length;

      final int newStringLength = str.length();
      final String chars = getText(start, end);
      if (chars.equals(str)) return;

      int newStartInString = 0;
      int newEndInString = newStringLength;
      while (newStartInString < newStringLength && start < end && str.charAt(newStartInString) == chars.charAt(start - oldStart)) {
        start++;
        newStartInString++;
      }

      while (end > start && newEndInString > newStartInString && str.charAt(newEndInString - 1) == chars.charAt(end - oldStart - 1)) {
        newEndInString--;
        end--;
      }

      //[mike] dirty hack for xml:
      //make sure that deletion of <t> in: <tag><t/><tag> doesn't remove t/><
      //which is perfectly valid but invalidates range markers
      final CharSequence charsSequence = myDocument.getCharsSequence();
      while (start < charsSequence.length() && end < charsSequence.length() && start > 0 &&
             charsSequence.subSequence(start, end).toString().endsWith("><") && charsSequence.charAt(start - 1) == '<') {
        start--;
        newStartInString--;
        end--;
        newEndInString--;
      }

      str = str.substring(newStartInString, newEndInString);
      length = end - start;

      final Pair<MutableTextRange, StringBuffer> fragment = getFragmentByRange(start, length);
      final StringBuffer fragmentReplaceText = fragment.getSecond();
      final int startInFragment = start - fragment.getFirst().getStartOffset();

      // text range adjustment
      final int lengthDiff = str.length() - length;
      final Iterator<Pair<MutableTextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
      boolean adjust = false;
      while (iterator.hasNext()) {
        final Pair<MutableTextRange, StringBuffer> pair = iterator.next();
        if (adjust) pair.getFirst().shift(lengthDiff);
        if (pair == fragment) adjust = true;
      }

      fragmentReplaceText.replace(startInFragment, startInFragment + length, str);
    }

    private String getText(final int start, final int end) {
      int currentOldDocumentOffset = 0;
      int currentNewDocumentOffset = 0;
      StringBuilder text = new StringBuilder();
      Iterator<Pair<MutableTextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
      while (iterator.hasNext() && currentNewDocumentOffset < end) {
        final Pair<MutableTextRange, StringBuffer> pair = iterator.next();
        final MutableTextRange range = pair.getFirst();
        final StringBuffer buffer = pair.getSecond();
        final int fragmentEndInNewDocument = range.getStartOffset() + buffer.length();

        if(range.getStartOffset() <= start && fragmentEndInNewDocument >= end){
          return buffer.substring(start - range.getStartOffset(), end - range.getStartOffset());
        }

        if(range.getStartOffset() >= start){
          final int effectiveStart = Math.max(currentNewDocumentOffset, start);
          text.append(myDocument.getCharsSequence(),
                      effectiveStart - currentNewDocumentOffset + currentOldDocumentOffset,
                      Math.min(range.getStartOffset(), end) - currentNewDocumentOffset + currentOldDocumentOffset);
          if(end > range.getStartOffset()){
            text.append(buffer.substring(0, Math.min(end - range.getStartOffset(), buffer.length())));
          }
        }

        currentOldDocumentOffset += range.getEndOffset() - currentNewDocumentOffset;
        currentNewDocumentOffset = fragmentEndInNewDocument;
      }

      if(currentNewDocumentOffset < end){
        final int effectiveStart = Math.max(currentNewDocumentOffset, start);
        text.append(myDocument.getCharsSequence(),
                    effectiveStart - currentNewDocumentOffset + currentOldDocumentOffset,
                    end- currentNewDocumentOffset + currentOldDocumentOffset);
      }

      return text.toString();
    }

    private Pair<MutableTextRange, StringBuffer> getFragmentByRange(int start, final int length) {
      final StringBuffer fragmentBuffer = new StringBuffer();
      int end = start + length;

      // restoring buffer and remove all subfragments from the list
      int documentOffset = 0;
      int effectiveOffset = 0;

      Iterator<Pair<MutableTextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
      while (iterator.hasNext() && effectiveOffset <= end) {
        final Pair<MutableTextRange, StringBuffer> pair = iterator.next();
        final MutableTextRange range = pair.getFirst();
        final StringBuffer buffer = pair.getSecond();
        int effectiveFragmentEnd = range.getStartOffset() + buffer.length();

        if(range.getStartOffset() <= start && effectiveFragmentEnd >= end) return pair;

        if(effectiveFragmentEnd >= start){
          final int effectiveStart = Math.max(effectiveOffset, start);
          if(range.getStartOffset() > start){
            fragmentBuffer.append(myDocument.getCharsSequence(),
                                  effectiveStart - effectiveOffset + documentOffset,
                                  Math.min(range.getStartOffset(), end)- effectiveOffset + documentOffset);
          }
          if(end >= range.getStartOffset()){
            fragmentBuffer.append(buffer);
            end = end > effectiveFragmentEnd ? end - (buffer.length() - range.getLength()) : range.getEndOffset();
            effectiveFragmentEnd = range.getEndOffset();
            start = Math.min(start, range.getStartOffset());
            iterator.remove();
          }
        }

        documentOffset += range.getEndOffset() - effectiveOffset;
        effectiveOffset = effectiveFragmentEnd;
      }

      if(effectiveOffset < end){
        final int effectiveStart = Math.max(effectiveOffset, start);
        fragmentBuffer.append(myDocument.getCharsSequence(),
                              effectiveStart - effectiveOffset + documentOffset,
                              end- effectiveOffset + documentOffset);
      }

      MutableTextRange newRange = new MutableTextRange(start, end);
      final Pair<MutableTextRange, StringBuffer> pair = new Pair<MutableTextRange, StringBuffer>(newRange, fragmentBuffer);
      for (Pair<MutableTextRange, StringBuffer> affectedFragment : myAffectedFragments) {
        MutableTextRange range = affectedFragment.getFirst();
        assert end <= range.getStartOffset() || range.getEndOffset() <= start : "Range :"+range+"; Added: "+newRange;
      }
      myAffectedFragments.add(pair);
      return pair;
    }
  }

  public static class MutableTextRange{
    private final int myLength;
    private int myStartOffset;

    public MutableTextRange(final int startOffset, final int endOffset) {
      myStartOffset = startOffset;
      myLength = endOffset - startOffset;
    }

    public int getStartOffset() {
      return myStartOffset;
    }

    public int getEndOffset() {
      return myStartOffset + myLength;
    }

    public int getLength() {
      return myLength;
    }

    public String toString() {
      return "[" + getStartOffset() + ", " + getEndOffset() + "]";
    }

    public void shift(final int lengthDiff) {
      myStartOffset += lengthDiff;
    }
  }
}