package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.AdvancedDnDSource;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* @author nik
*/
public class LayoutTree extends SimpleDnDAwareTree implements AdvancedDnDSource {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTree");
  private final ArtifactEditorImpl myArtifactsEditor;

  public LayoutTree(ArtifactEditorImpl artifactsEditor) {
    myArtifactsEditor = artifactsEditor;
    setRootVisible(true);
    setShowsRootHandles(false);
    setCellEditor(new LayoutTreeCellEditor());
    DnDManager.getInstance().registerSource(this);
  }

  public void addSubtreeToUpdate(DefaultMutableTreeNode newNode) {
    AbstractTreeBuilder.getBuilderFor(this).addSubtreeToUpdate(newNode);
  }

  @Override
  protected void configureUiHelper(TreeUIHelper helper) {
    final Convertor<TreePath, String> convertor = new Convertor<TreePath, String>() {
      public String convert(final TreePath path) {
        final SimpleNode node = getNodeFor(path);
        if (node instanceof PackagingElementNode) {
          return ((PackagingElementNode<?>)node).getElementPresentation().getSearchName();
        }
        return "";
      }
    };
    new TreeSpeedSearch(this, convertor, true);
    helper.installToolTipHandler(this);
  }

  private List<PackagingElementNode<?>> getNodesToDrag() {
    return getSelection().getNodes();
  }

  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return !getNodesToDrag().isEmpty();
  }

  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    return new DnDDragStartBean(new LayoutNodesDraggingObject(myArtifactsEditor, getNodesToDrag()));
  }

  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    final List<PackagingElementNode<?>> nodes = getNodesToDrag();
    if (nodes.size() == 1) {
      return DnDAwareTree.getDragImage(this, getPathFor(nodes.get(0)), dragOrigin);
    }
    return DnDAwareTree.getDragImage(this, ProjectBundle.message("drag.n.drop.text.0.packaging.elements", nodes.size()), dragOrigin);
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(int gestureModifiers) {
  }

  public void dispose() {
    DnDManager.getInstance().unregisterSource(this);
  }

  public LayoutTreeSelection getSelection() {
    return new LayoutTreeSelection(this);
  }

  @Nullable
  public PackagingElement<?> getElementByPath(TreePath path) {
    final SimpleNode node = getNodeFor(path);
    if (node instanceof PackagingElementNode) {
      final List<? extends PackagingElement<?>> elements = ((PackagingElementNode<?>)node).getPackagingElements();
      if (elements.size() == 1) {
        return elements.get(0);
      }
    }
    return null;
  }

  public PackagingElementNode<?> getRootPackagingNode() {
    return (PackagingElementNode<?>)getNodeFor(new TreePath(getRootNode()));
  }

  public DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getModel().getRoot();
  }

  public List<PackagingElementNode<?>> findNodes(final Collection<? extends PackagingElement<?>> elements) {
    final List<PackagingElementNode<?>> nodes = new ArrayList<PackagingElementNode<?>>();
    TreeUtil.traverseDepth(getRootNode(), new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        final Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
        if (userObject instanceof PackagingElementNode) {
          final PackagingElementNode<?> packagingNode = (PackagingElementNode<?>)userObject;
          final List<? extends PackagingElement<?>> nodeElements = packagingNode.getPackagingElements();
          if (ContainerUtil.intersects(nodeElements, elements)) {
            nodes.add(packagingNode);
          }
        }
        return true;
      }
    });
    return nodes;
  }

  public void addSubtreeToUpdate(final PackagingElementNode elementNode) {
    final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(getRootNode(), elementNode);
    if (node != null) {
      addSubtreeToUpdate(node);
    }
  }

  private class LayoutTreeCellEditor extends DefaultCellEditor {
    public LayoutTreeCellEditor() {
      super(new JTextField());
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
      final JTextField field = (JTextField)super.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row);
      final Object node = ((DefaultMutableTreeNode)value).getUserObject();
      final PackagingElement<?> element = ((PackagingElementNode)node).getElementIfSingle();
      LOG.assertTrue(element != null);
      final String name = ((CompositePackagingElement)element).getName();
      field.setText(name);
      int i = name.lastIndexOf('.');
      field.setSelectionStart(0);
      field.setSelectionEnd(i != -1 ? i : name.length());
      return field;
    }

    @Override
    public boolean stopCellEditing() {
      final String newValue = ((JTextField)editorComponent).getText();
      final TreePath path = getEditingPath();
      final Object node = getNodeFor(path);
      CompositePackagingElement currentElement = null;
      if (node instanceof PackagingElementNode) {
        final PackagingElement<?> element = ((PackagingElementNode)node).getElementIfSingle();
        if (element instanceof CompositePackagingElement) {
          currentElement = (CompositePackagingElement)element;
        }
      }
      final boolean stopped = super.stopCellEditing();
      if (stopped && currentElement != null) {
        currentElement.rename(newValue);
        addSubtreeToUpdate((DefaultMutableTreeNode)path.getLastPathComponent());
      }
      return stopped;
    }
  }
}